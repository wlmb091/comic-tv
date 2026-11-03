package com.pelican.comictv.archive

import com.github.junrar.Archive
import com.github.junrar.rarfile.HostSystem
import com.github.junrar.volume.FileVolume
import com.github.junrar.volume.VolumeManager
import java.io.File
import java.io.OutputStream

/**
 * RAR decoder for the JVM tests only (registered in META-INF/services). The app decodes with libarchive, which the
 * device test LibArchiveRarTest covers; here the same validation and limits run against the same fixtures.
 */
class JunrarTestBackend : RarBackend {
    private fun open(file: File): Archive {
        val volumes = VolumeManager { archive: Archive, previous: com.github.junrar.volume.Volume? -> if (previous == null) FileVolume(archive, file) else null }
        return Archive(volumes, com.github.junrar.ArchiveOptions.builder().maxDictionarySize(32L * 1024 * 1024).build())
    }

    override fun entries(file: File, maxEntries: Int): List<RarEntry> = open(file).use { archive ->
        archive.fileHeaders.take(maxEntries).map { h ->
            var flags = 0
            if (h.isDirectory) flags = flags or RarEntry.DIRECTORY
            if (h.redirection != null || h.hostOS == HostSystem.unix && h.fileAttr and 0xf000 == 0xa000) flags = flags or RarEntry.LINK
            if (h.isEncrypted) flags = flags or RarEntry.ENCRYPTED
            if (h.isUnpSizeUnknown) flags = flags or RarEntry.SIZE_UNKNOWN
            RarEntry(h.fileName.replace('\\', '/'), h.fullUnpackSize, flags)
        }
    }

    override fun extract(file: File, index: Int, solid: Boolean, sink: RarSink): Long = open(file).use { archive ->
        val headers = archive.fileHeaders
        var total = 0L
        for (i in (if (solid) 0 else index)..index) {
            val header = headers[i]
            if (header.isDirectory) continue
            val target = i == index
            val out = object : OutputStream() {
                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    val chunk = if (off == 0) b else b.copyOfRange(off, off + len)
                    sink.chunk(target, chunk, len)
                    if (target) total += len
                }
            }
            archive.extractFile(header, out)
        }
        total
    }
}
