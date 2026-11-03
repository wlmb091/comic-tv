package com.pelican.comictv.tv

import android.content.Context
import androidx.annotation.MainThread
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/** Public snapshot contains no source URL, headers, tracker address or addon credentials. */
data class TvPlaybackState(
    val active: Boolean = false,
    val title: String = "",
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val message: String = "",
    val torrent: Boolean = false,
    val playerReady: Boolean = false,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TvPlaybackController(private val context: Context, private val repository: TvRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(TvPlaybackState())
    val state = mutable.asStateFlow()
    var player: ExoPlayer? = null
        private set
    private var job: Job? = null
    private var ticker: Job? = null
    private var generation = 0L
    private var torrent: TvTorrentSession? = null
    private var meta: TvMeta? = null
    private var video: TvVideo? = null
    private val progressWrites = Channel<TvProgress>(Channel.UNLIMITED)

    init {
        // Keep writes in playback order: an older periodic save must not overtake the final stop save.
        scope.launch(Dispatchers.IO) {
            for (p in progressWrites) runCatching { repository.progress(p.meta, p.video, p.positionMs, p.durationMs) }
        }
    }

    @MainThread fun play(meta: TvMeta, video: TvVideo, stream: TvStream, position: Long = 0) {
        stop()
        this.meta = meta
        this.video = video
        val token = ++generation
        val isTorrent = stream.infoHash.isNotBlank() || stream.url.startsWith("magnet:")
        mutable.value = TvPlaybackState(
            active = true,
            title = meta.name + if (video.id != meta.id) " · ${video.title}" else "",
            buffering = true,
            torrent = isTorrent,
            message = if (isTorrent) "Obtendo metadados do torrent…" else "Abrindo vídeo…",
        )
        job = scope.launch {
            try {
                val factory: DataSource.Factory
                val uri: String
                if (isTorrent) {
                    val session = TvTorrentSession(context, repository.state.value.cacheGiB)
                    torrent = session
                    session.prepare(stream)
                    ensureActive()
                    if (token != generation) return@launch
                    factory = DataSource.Factory { TorrentDataSource(session) }
                    uri = "torrent://video"
                } else {
                    require(StremioLinks.http(stream.url)) { "Fonte de vídeo incompatível." }
                    val headers = stream.headers.filter { (k, v) ->
                        k.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}")) && v.length <= 8192 &&
                            v.none { it == '\r' || it == '\n' || it == '\u0000' } &&
                            k.lowercase() !in setOf("host", "content-length", "connection")
                    }
                    factory = DefaultDataSource.Factory(
                        context,
                        DefaultHttpDataSource.Factory().setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
                            .setDefaultRequestProperties(headers),
                    )
                    uri = stream.url
                }
                ensureActive()
                if (token != generation) return@launch
                val exo = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build()
                player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (token == generation) scope.launch {
                            fail(token, "Não foi possível reproduzir esta fonte. Volte e escolha outra.")
                        }
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (token == generation) mutable.update { it.copy(playing = isPlaying) }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (token == generation) mutable.update {
                            it.copy(
                                buffering = playbackState == Player.STATE_BUFFERING,
                                message = if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) "" else it.message,
                            )
                        }
                    }
                })
                // Force a new snapshot when the view must attach a newly created player after metadata loading.
                mutable.update { it.copy(playerReady = true) }
                exo.setMediaItem(MediaItem.fromUri(uri))
                exo.prepare()
                if (position > 0) exo.seekTo(position)
                exo.playWhenReady = true
                ticker = scope.launch {
                    var ticks = 0
                    while (isActive && token == generation) {
                        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
                        mutable.update { it.copy(positionMs = exo.currentPosition.coerceAtLeast(0), durationMs = duration) }
                        if (++ticks % 5 == 0) saveProgress()
                        delay(1000)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(token, if (isTorrent && e is java.io.IOException) e.message ?: "Torrent indisponível." else "Não foi possível iniciar esta fonte. Volte e escolha outra.")
            } catch (_: LinkageError) {
                fail(token, "Reprodução indisponível nesta arquitetura do aparelho.")
            }
        }
    }

    private fun saveProgress() {
        val m = meta ?: return
        val v = video ?: return
        val exo = player
        val current = exo?.currentPosition?.coerceAtLeast(0) ?: state.value.positionMs
        val duration = exo?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: state.value.durationMs
        if (current > 0) progressWrites.trySend(TvProgress(m, v, current, duration.coerceAtLeast(0)))
    }

    private fun releaseResources() {
        // release() can synchronously wait for the loader: unblock torrent reads before invoking it.
        val oldTorrent = torrent
        oldTorrent?.cancelPendingReads()
        torrent = null
        val oldPlayer = player
        player = null
        runCatching { oldPlayer?.release() }
        if (oldTorrent != null) scope.launch(Dispatchers.IO) { runCatching { oldTorrent.close() } }
    }

    private fun fail(token: Long, message: String) {
        if (token != generation) return
        saveProgress()
        generation++
        ticker?.cancel()
        ticker = null
        releaseResources()
        mutable.update { it.copy(buffering = false, playing = false, playerReady = false, message = message) }
    }

    @MainThread fun pause() { player?.pause() }
    @MainThread fun resume() { player?.play() }
    @MainThread fun seekTo(ms: Long) {
        val duration = state.value.durationMs
        player?.seekTo(ms.coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE))
    }
    @MainThread fun stop() {
        saveProgress()
        generation++
        torrent?.cancelPendingReads()
        job?.cancel()
        job = null
        ticker?.cancel()
        ticker = null
        releaseResources()
        meta = null
        video = null
        mutable.value = TvPlaybackState()
    }
}
