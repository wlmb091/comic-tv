package com.pelican.comictv.tv

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.*
import kotlinx.coroutines.*
import org.libtorrent4j.*
import org.libtorrent4j.alerts.*
import java.io.*
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicLong

/** What the player shows while a torrent connects and fills the start of the video. [candidates] are known peers not yet connected. */
data class TorrentStats(val peers:Int,val seeds:Int,val downloadRate:Long,val done:Long,val target:Long,val metadata:Boolean,val candidates:Int=0)

/**
 * The one native session of the app. It stays on while the app is open, so the DHT is already warm when the next
 * video starts; each video only adds and removes its own torrent. Two minutes after the app leaves the screen with
 * no torrent open, it stops.
 */
internal object TvTorrentEngine {
    private var manager: SessionManager? = null
    private var users = 0
    private var foreground = true
    private var idle: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushes = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()
    private const val IDLE_MS = 2 * 60 * 1000L

    /** Starts the session when needed. Must be paired with [release]. */
    @Synchronized fun acquire(parent: File): SessionManager {
        idle?.cancel(); idle = null
        val running = manager ?: start(parent)
        users++
        return running
    }

    @Synchronized fun release() { users = (users - 1).coerceAtLeast(0); scheduleIdle() }

    /** Opening the TV tab starts the engine early, so the DHT finds nodes before the first video. */
    fun warm(context: Context) {
        scope.launch { runCatching { acquire(File(context.cacheDir, "tv-torrents")); release() } }
    }

    @Synchronized fun foreground(visible: Boolean) {
        foreground = visible
        if (visible) { idle?.cancel(); idle = null } else scheduleIdle()
    }

    fun flushCount(hash: String): AtomicLong = flushes.getOrPut(hash.lowercase()) { AtomicLong() }

    private fun start(parent: File): SessionManager {
        parent.mkdirs()
        // No torrent is open before the first start, so every cache left in the folder was abandoned.
        parent.listFiles()?.filter { it.name.matches(Regex("[0-9a-fA-F-]{36}")) }?.forEach { runCatching { it.deleteRecursively() } }
        val settings = SettingsPack().connectionsLimit(60).activeDownloads(2)
        settings.setMaxMetadataSize(16 * 1024 * 1024)
        val session = SessionManager()
        session.start(SessionParams(settings))
        session.addListener(object : AlertListener {
            override fun types() = intArrayOf(AlertType.CACHE_FLUSHED.swig())
            override fun alert(alert: Alert<*>) {
                if (alert is CacheFlushedAlert) runCatching { flushCount(alert.handle().infoHash().toHex()).incrementAndGet() }
            }
        })
        manager = session
        return session
    }

    private fun scheduleIdle() {
        if (users > 0 || foreground || manager == null) return
        idle?.cancel()
        idle = scope.launch {
            delay(IDLE_MS)
            synchronized(this@TvTorrentEngine) {
                if (users > 0 || foreground) return@launch
                val session = manager
                manager = null
                flushes.clear()
                runCatching { session?.stop() }
            }
        }
    }
}

/** One torrent in the shared engine, with its private cache. All JNI calls for it finish before it is removed. */
class TvTorrentSession(context: Context, private val quotaGiB: Int, private val publicTrackers: Boolean = true) : Closeable {
    private val parent = File(context.cacheDir, "tv-torrents")
    private val directory = File(parent, java.util.UUID.randomUUID().toString())
    @Volatile private var stopped = false
    private var acquired = false
    private var flushes = AtomicLong()
    private var manager: SessionManager? = null
    @Volatile private var handle: TorrentHandle? = null
    private val statsGuard = Any()
    @Volatile private var startTarget = 0L
    private var target: File? = null
    var size: Long = 0
        private set
    /** Name of the chosen video inside the torrent, for the external player's content type. */
    val fileName: String get() = target?.name.orEmpty()
    private var offset = 0L
    private var pieceSize = 0
    private val flushed = HashSet<Int>()

    suspend fun prepare(stream: TvStream): TvTorrentSession = withContext(Dispatchers.IO) {
        try {
            require(quotaGiB in setOf(1, 2, 4, 8, 16)) { "Limite de cache inválido." }
            val magnet = TorrentLinks.magnet(stream.url, stream.infoHash, stream.sources, publicTrackers)
                ?: throw IOException("Esta fonte torrent não tem um identificador compatível.")
            val params = AddTorrentParams.parseMagnetUri(magnet)
            val hash = params.infoHashes.best
            val session = synchronized(this@TvTorrentSession) {
                checkOpen()
                TvTorrentEngine.acquire(parent).also { acquired = true; manager = it }
            }
            // The same video played again: wait until the previous copy has left the engine.
            val free = System.nanoTime() + 15_000_000_000L
            while (session.find(hash)?.isValid == true) {
                currentCoroutineContext().ensureActive()
                if (System.nanoTime() > free) throw IOException("O vídeo anterior ainda está sendo encerrado. Tente de novo em alguns segundos.")
                delay(100)
            }
            synchronized(this@TvTorrentSession) {
                checkOpen()
                check(directory.mkdirs()) { "Não foi possível preparar o cache do torrent." }
                flushes = TvTorrentEngine.flushCount(hash.toHex())
                params.savePath = directory.absolutePath
                params.flags = params.flags
                    .and_(TorrentFlags.AUTO_MANAGED.or_(TorrentFlags.PAUSED).inv())
                    .or_(TorrentFlags.UPLOAD_MODE).or_(TorrentFlags.DEFAULT_DONT_DOWNLOAD)
                session.swig().async_add_torrent(params.swig())
                pendingHash = hash
            }
            // The player suggests another source after a while; this one keeps trying until the user leaves.
            val deadline = System.nanoTime() + 10 * 60_000_000_000L
            var ready = false
            while (!ready) {
                currentCoroutineContext().ensureActive()
                synchronized(this@TvTorrentSession) {
                    checkOpen()
                    val h = manager!!.find(pendingHash!!)
                    if (h != null && h.isValid) {
                        handle = h
                        val status = h.status()
                        if (status.errorCode().value != 0) throw IOException("O torrent informou um erro ao obter metadados.")
                        ready = status.hasMetadata()
                    }
                }
                if (System.nanoTime() >= deadline) throw IOException("Não foi possível obter os metadados do torrent. Tente outra fonte.")
                if (!ready) delay(50)
            }
            synchronized(this@TvTorrentSession) {
                checkOpen()
                val h = handle ?: throw IOException("Torrent encerrado.")
                val info = h.torrentFile() ?: throw IOException("Metadados do torrent indisponíveis.")
                val fs = info.files()
                require(fs.numFiles() in 1..20_000) { "O torrent tem arquivos demais." }
                val root = directory.canonicalFile.toPath()
                for (i in 0 until fs.numFiles()) {
                    val path = fs.filePath(i)
                    val file = File(directory, path).canonicalFile
                    require(!File(path).isAbsolute && file.toPath().startsWith(root) && file.toPath() != root) {
                        "O torrent contém um caminho inválido."
                    }
                    require(!fs.fileFlags(i).and_(FileStorage.FLAG_SYMLINK).non_zero()) { "Links simbólicos não são aceitos." }
                }
                val index = stream.fileIdx ?: (0 until fs.numFiles())
                    .filter { fs.filePath(it).substringAfterLast('.').lowercase() in VIDEO_EXTENSIONS }
                    .maxByOrNull { fs.fileSize(it) }
                    ?: throw IOException("Nenhum vídeo encontrado no torrent.")
                require(index in 0 until fs.numFiles()) { "Arquivo de vídeo não encontrado no torrent." }
                require(!fs.fileFlags(index).and_(FileStorage.FLAG_PAD_FILE).non_zero()) { "O arquivo selecionado não é um vídeo." }
                size = fs.fileSize(index)
                require(size > 0) { "O arquivo selecionado está vazio." }
                val allowance = minOf(quotaGiB.toLong() * GIB, (directory.usableSpace - RESERVE).coerceAtLeast(0))
                // A boundary piece may need bytes from a skipped neighbour in the part-file.
                val budget = Math.addExact(size, info.pieceLength().toLong() * 2)
                require(budget <= allowance) {
                    "O vídeo não cabe no cache de torrents ou no espaço livre. Aumente o limite ou escolha uma fonte menor."
                }
                offset = fs.fileOffset(index)
                pieceSize = info.pieceLength()
                require(pieceSize in 16_384..16 * 1024 * 1024) { "Tamanho de peça não compatível." }
                require(offset >= 0 && offset <= Long.MAX_VALUE - size) { "Tamanho de vídeo inválido." }
                target = File(directory, fs.filePath(index)).canonicalFile
                h.prioritizeFiles(Array(fs.numFiles()) { if (it == index) Priority.DEFAULT else Priority.IGNORE })
                h.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                h.unsetFlags(TorrentFlags.UPLOAD_MODE.or_(TorrentFlags.DEFAULT_DONT_DOWNLOAD))
                h.resume()
                val first = (offset / pieceSize).toInt()
                val last = ((offset + size - 1) / pieceSize).toInt()
                h.setSequentialRange(first, last)
                h.setPieceDeadline(first, 0)
                h.setPieceDeadline(last, 1000)
                // Rough amount the player reads before the first frame: two pieces, at least 8 MiB.
                startTarget = minOf(size, maxOf(pieceSize.toLong() * 2, 8L * 1024 * 1024))
            }
            this@TvTorrentSession
        } catch (e: CancellationException) {
            close()
            throw e
        } catch (e: Exception) {
            close()
            // Third-party parser/native errors can contain the configured magnet or tracker URL.
            throw IOException(if (e is IOException || e is IllegalArgumentException) publicError(e) else "Não foi possível iniciar o torrent.")
        } catch (e: LinkageError) {
            close()
            throw IOException("Torrents indisponíveis nesta arquitetura do aparelho.")
        }
    }

    private var pendingHash: Sha1Hash? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Synchronized fun read(position: Long, buffer: ByteArray, start: Int, length: Int, cancelled: () -> Boolean = { false }): Int {
        require(position >= 0 && start >= 0 && length >= 0 && start <= buffer.size - length)
        if (length == 0) return 0
        checkOpen()
        if (position >= size) return C.RESULT_END_OF_INPUT
        val h = handle ?: throw IOException("Torrent encerrado.")
        val absolute = offset + position
        val piece = (absolute / pieceSize).toInt()
        val count = minOf(length.toLong(), size - position, pieceSize - absolute % pieceSize).toInt()
        val until = System.nanoTime() + 60_000_000_000L
        h.clearPieceDeadlines()
        h.setPieceDeadline(piece, 0)
        for (p in piece + 1..minOf(piece + 8, ((offset + size - 1) / pieceSize).toInt())) h.setPieceDeadline(p, (p - piece) * 300)
        while (!h.havePiece(piece)) {
            checkRead(until, cancelled)
            waitForData(50)
        }
        checkRead(until, cancelled)
        if (piece !in flushed) {
            val serial = flushes.get()
            h.flushCache()
            while (flushes.get() == serial) {
                checkRead(until, cancelled)
                waitForData(30)
            }
            checkRead(until, cancelled)
            flushed.add(piece)
        }
        if (directory.usableSpace < 64L * 1024 * 1024) throw IOException("Espaço livre insuficiente para continuar o torrent.")
        RandomAccessFile(target ?: throw IOException("Vídeo indisponível."), "r").use {
            it.seek(position)
            it.readFully(buffer, start, count)
        }
        return count
    }

    /** Light status for the player; never waits for a pending read. */
    fun stats(): TorrentStats? = synchronized(statsGuard) {
        if (stopped) return null
        val h = handle ?: return TorrentStats(0, 0, 0, 0, 0, false)
        runCatching {
            if (!h.isValid) return null
            val s = h.status()
            TorrentStats(s.numPeers(), s.numSeeds(), s.downloadPayloadRate().toLong(), s.totalWantedDone(), startTarget, s.hasMetadata(),
                maxOf(s.listPeers(), s.connectCandidates()))
        }.getOrNull()
    }

    private fun checkOpen() { if (stopped) throw IOException("Torrent encerrado.") }
    private fun checkRead(deadline: Long, cancelled: () -> Boolean) {
        checkOpen()
        if (cancelled() || Thread.currentThread().isInterrupted) throw InterruptedIOException("Leitura cancelada.")
        if (System.nanoTime() > deadline) throw IOException("O torrent não recebeu dados suficientes. Tente outra fonte.")
    }
    private fun waitForData(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Leitura cancelada.")
        }
    }

    /** Non-blocking signal used before Player.release(), whose loader can be waiting inside read(). */
    fun cancelPendingReads() { stopped = true }

    override fun close() {
        cancelPendingReads()
        // A status call already in progress finishes before the native session stops.
        synchronized(statsGuard) { }
        synchronized(this) {
            val session = manager
            val hash = pendingHash
            manager = null
            handle = null
            try {
                if (session != null && hash != null) {
                    // The engine stays on; only this torrent leaves it, with its files.
                    session.find(hash)?.takeIf { it.isValid }?.let { session.remove(it, SessionHandle.DELETE_FILES) }
                    val until = System.nanoTime() + 10_000_000_000L
                    while (session.find(hash)?.isValid == true && System.nanoTime() < until) Thread.sleep(50)
                    // If removal cannot be confirmed, retain this cache rather than deleting beneath a writer.
                    if (session.find(hash)?.isValid != true) runCatching { deleteCache(directory) }
                } else runCatching { deleteCache(directory) }
            } finally {
                if (acquired) { acquired = false; TvTorrentEngine.release() }
            }
        }
    }

    private fun deleteCache(file: File) {
        val path = file.toPath().toAbsolutePath().normalize()
        val root = parent.toPath().toAbsolutePath().normalize()
        if (!path.startsWith(root) || path == root || !Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult { Files.deleteIfExists(file); return FileVisitResult.CONTINUE }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult { if (exc != null) throw exc; Files.deleteIfExists(dir); return FileVisitResult.CONTINUE }
        })
    }

    private fun publicError(e: Exception): String {
        val message = e.message.orEmpty()
        return if (message.startsWith("O ") || message.startsWith("Não foi possível ") || message.startsWith("Esta fonte ") || message.startsWith("Arquivo de ") || message.startsWith("Nenhum vídeo ") || message.startsWith("Links simbólicos ")) message
        else "Não foi possível preparar o torrent. Verifique a fonte e o espaço livre."
    }

    companion object {
        private const val GIB = 1024L * 1024 * 1024
        private const val RESERVE = 256L * 1024 * 1024
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "m4v", "ts", "mov")
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TorrentDataSource(private val torrent: TvTorrentSession) : BaseDataSource(false) {
    private var position = 0L
    private var remaining = 0L
    @Volatile private var closed = true
    private var opened = false
    private var uri: Uri? = null
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        position = dataSpec.position
        if (position > torrent.size) throw DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE)
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) torrent.size - position else minOf(dataSpec.length, torrent.size - position)
        uri = dataSpec.uri
        closed = false
        opened = true
        transferStarted(dataSpec)
        return remaining
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (closed) throw IOException("Leitura encerrada.")
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val n = torrent.read(position, buffer, offset, minOf(length.toLong(), remaining).toInt()) { closed }
        if (n > 0) { position += n; remaining -= n; bytesTransferred(n) }
        return n
    }
    override fun getUri() = uri
    override fun close() { closed = true; uri = null; if (opened) { opened = false; transferEnded() } }
}

