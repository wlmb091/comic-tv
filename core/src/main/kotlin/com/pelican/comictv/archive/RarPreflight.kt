package com.pelican.comictv.archive

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Bound metadata allocations before Junrar constructs its in-memory header list. */
internal fun preflightRar(file: File, limits: ArchiveLimits) {
    RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        if (length > limits.maxArchiveBytes) throw ComicArchiveException("O arquivo excede o limite de 1 GiB.")
        input.seek(6)
        val rar5 = input.read() == 1
        var position = if (rar5) 8L else 7L
        var count = 0
        var metadata = 0L
        fun invalid(): Nothing = throw ComicArchiveException("O arquivo RAR está corrompido ou incompleto.")
        fun byte(): Int = input.read().also { if (it < 0) invalid() }
        fun uint(): Long = (0..3).fold(0L) { value, i -> value or (byte().toLong() shl (8 * i)) }
        fun vint(): Long {
            var value = 0L
            for (i in 0..8) {
                val b = byte()
                value = value or ((b and 0x7f).toLong() shl (7 * i))
                if (b and 0x80 == 0) return value
            }
            invalid()
        }
        while (position < length) {
            if (Thread.currentThread().isInterrupted) throw IOException("Leitura cancelada.")
            if (++count > limits.maxEntries + 2) throw ComicArchiveException("O arquivo contém entradas demais.")
            input.seek(position)
            val headerSize: Long
            var dataSize = 0L
            if (rar5) {
                if (length - position < 5) invalid()
                uint() // CRC is verified by the decoder, not trusted here.
                val remainingHeader = vint()
                val bodyStart = input.filePointer
                headerSize = bodyStart - position + remainingHeader
                if (remainingHeader > 1024 * 1024 || headerSize > length - position) invalid()
                val type = vint()
                val flags = vint()
                if (type == 4L) throw ComicArchiveException("RAR protegido por senha não é suportado.")
                if (flags and 1L != 0L) vint()
                if (flags and 2L != 0L) dataSize = vint()
                if (input.filePointer > position + headerSize) invalid()
            } else {
                if (length - position < 7) invalid()
                byte(); byte()
                val type = byte()
                val flags = byte() or (byte() shl 8)
                headerSize = (byte() or (byte() shl 8)).toLong()
                if (headerSize < 7 || headerSize > length - position) invalid()
                if (type == 0x73 && flags and 0x80 != 0) throw ComicArchiveException("RAR protegido por senha não é suportado.")
                if (flags and 0x8000 != 0) {
                    if (headerSize < 11) invalid()
                    dataSize = uint()
                    if (type == 0x74 && flags and 0x100 != 0) {
                        if (headerSize < 40) invalid()
                        input.seek(position + 32)
                        val high = uint()
                        if (high != 0L) invalid() // Archive itself is capped below 4 GiB.
                    }
                }
            }
            metadata += headerSize
            if (metadata > 8L * 1024 * 1024) throw ComicArchiveException("O arquivo RAR contém metadados demais.")
            if (headerSize > length - position || dataSize > length - position - headerSize) invalid()
            position += headerSize + dataSize
        }
    }
}
