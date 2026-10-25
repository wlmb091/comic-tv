package com.pelican.comictv.archive

import java.io.File
import java.io.RandomAccessFile

/** Bound directory allocation and reject links/encryption before java.util.zip parses entries. */
internal fun guardCollectionZip(file: File, maxEntries: Int) {
    fun invalid(): Nothing = throw ComicArchiveException("ZIP de coleção inválido, dividido, protegido ou com metadados demais.")
    RandomAccessFile(file, "r").use { input ->
        val length = input.length()
        if (length < 22) invalid()
        val tail = ByteArray(minOf(length, 65_557L).toInt())
        input.seek(length - tail.size); input.readFully(tail)
        fun u16(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 255) or
            ((bytes[offset + 1].toInt() and 255) shl 8)
        fun u32(bytes: ByteArray, offset: Int): Long = u16(bytes, offset).toLong() or
            (u16(bytes, offset + 2).toLong() shl 16)
        val end = (tail.size - 22 downTo 0).firstOrNull { i ->
            u32(tail, i) == 0x06054b50L && i + 22 + u16(tail, i + 20) == tail.size
        } ?: invalid()
        val count = u16(tail, end + 10)
        val size = u32(tail, end + 12)
        val offset = u32(tail, end + 16)
        // ZIP64 and multi-volume outer packages are unnecessary within the stated 1 GiB/5000 limits.
        if (u16(tail, end + 4) != 0 || u16(tail, end + 6) != 0 ||
            u16(tail, end + 8) != count || count > maxEntries ||
            size > 8L * 1024 * 1024 || offset > length - size ||
            offset + size != length - tail.size + end) invalid()
        input.seek(offset)
        val header = ByteArray(46)
        repeat(count) {
            if (Thread.currentThread().isInterrupted) throw java.io.IOException("Importação cancelada.")
            if (input.filePointer > offset + size - header.size) invalid()
            input.readFully(header)
            if (u32(header, 0) != 0x02014b50L || u16(header, 8) and 1 != 0 || u16(header, 34) != 0) invalid()
            val mode = (u32(header, 38) shr 16).toInt() and 0xf000
            if (mode == 0xa000) throw ComicArchiveException("A coleção ZIP contém links não suportados.")
            val nameLength = u16(header, 28)
            val extra = u16(header, 30) + u16(header, 32)
            if (nameLength > 4096 || nameLength + extra > offset + size - input.filePointer) invalid()
            input.seek(input.filePointer + nameLength + extra)
        }
        if (input.filePointer != offset + size) invalid()
    }
}
