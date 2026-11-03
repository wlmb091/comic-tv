package com.pelican.comictv.archive

import java.io.File
import java.io.IOException
import java.util.ServiceLoader

/** One RAR header in archive order, as the decoder reports it. */
class RarEntry(val name: String, val size: Long, private val flags: Int) {
    val directory get() = flags and DIRECTORY != 0
    val link get() = flags and LINK != 0
    val encrypted get() = flags and ENCRYPTED != 0
    val sizeKnown get() = flags and SIZE_UNKNOWN == 0
    /** Device, FIFO and other entries that are neither files nor folders. */
    val special get() = flags and SPECIAL != 0
    companion object { const val DIRECTORY = 1; const val LINK = 2; const val ENCRYPTED = 4; const val SIZE_UNKNOWN = 8; const val SPECIAL = 16 }
}

/** Receives decoded bytes; throwing stops the decoder (size limits, cancellation). */
fun interface RarSink { fun chunk(target: Boolean, buffer: ByteArray, length: Int) }

/** RAR decoding. The app uses libarchive; JVM tests plug in another decoder through ServiceLoader. */
interface RarBackend {
    fun entries(file: File, maxEntries: Int): List<RarEntry>
    /**
     * Streams entry [index] (archive order) to [sink]. With [solid], earlier files are decoded first and reach the sink
     * with target=false, so their size is bounded and the work can be cancelled. Returns the size of the entry.
     */
    fun extract(file: File, index: Int, solid: Boolean, sink: RarSink): Long
}

object RarBackends {
    val current: RarBackend by lazy {
        ServiceLoader.load(RarBackend::class.java).firstOrNull() ?: LibArchiveRar
    }
}

/** libarchive 3.8.11 (BSD-2-Clause) through libcomictv_archive.so; see native/archive. */
object LibArchiveRar : RarBackend {
    private val loaded: Throwable? = try { System.loadLibrary("comictv_archive"); null } catch (e: Throwable) { e }

    private fun ready() { loaded?.let { throw ComicArchiveException("A leitura de RAR não está disponível neste aparelho.", it) } }

    fun version(): String { ready(); return nativeVersion() }

    override fun entries(file: File, maxEntries: Int): List<RarEntry> {
        ready()
        val list = ArrayList<RarEntry>()
        nativeEntries(file.absolutePath, maxEntries, object : EntrySink {
            override fun entry(name: ByteArray, size: Long, flags: Int) { list += RarEntry(String(name, Charsets.UTF_8), size, flags) }
        })
        return list
    }

    override fun extract(file: File, index: Int, solid: Boolean, sink: RarSink): Long {
        ready()
        val size = nativeExtract(file.absolutePath, index, solid, object : ChunkSink {
            override fun chunk(target: Boolean, buffer: ByteArray, length: Int) = sink.chunk(target, buffer, length)
        })
        if (size < 0) throw IOException("Página RAR não encontrada.")
        return size
    }

    interface EntrySink { fun entry(name: ByteArray, size: Long, flags: Int) }
    interface ChunkSink { fun chunk(target: Boolean, buffer: ByteArray, length: Int) }

    @JvmStatic private external fun nativeEntries(path: String, max: Int, sink: EntrySink): Int
    @JvmStatic private external fun nativeExtract(path: String, index: Int, solidReplay: Boolean, sink: ChunkSink): Long
    @JvmStatic private external fun nativeVersion(): String
}
