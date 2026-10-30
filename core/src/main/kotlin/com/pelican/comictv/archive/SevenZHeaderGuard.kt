package com.pelican.comictv.archive

/** Cap decoded encoded-header sizes BEFORE Commons Compress allocates them. */
internal fun guardEncodedSevenZHeader(header: ByteArray) {
    if (header.firstOrNull()?.toInt() != 0x17) return
    var offset = 1
    fun invalid(): Nothing = throw ComicArchiveException("Os metadados comprimidos do 7z excedem os limites ou não são compatíveis.")
    fun byte(): Int {
        if (offset >= header.size) invalid()
        return header[offset++].toInt() and 255
    }
    fun number(): Long {
        val first = byte()
        var mask = 0x80
        var result = 0L
        for (i in 0..7) {
            if (first and mask == 0) return result or ((first and (mask - 1)).toLong() shl (8 * i))
            result = result or (byte().toLong() shl (8 * i))
            mask = mask ushr 1
        }
        if (result < 0) invalid()
        return result
    }
    fun expect(value: Int) { if (byte() != value) invalid() }
    fun skip(count: Long) {
        if (count < 0 || count > header.size - offset) invalid()
        offset += count.toInt()
    }
    // Commons Compress reads one encoded-header folder and its first packed stream.
    expect(6); number()
    if (number() != 1L) invalid()
    expect(9); number()
    var property = byte()
    if (property == 0x0a) {
        val allDefined = byte()
        if (allDefined == 1) skip(4)
        else if (allDefined == 0) { if (byte() and 0x80 != 0) skip(4) }
        else invalid()
        property = byte()
    }
    if (property != 0) invalid()
    expect(7); expect(0x0b)
    if (number() != 1L || byte() != 0) invalid()
    val coders = number()
    if (coders !in 1..8) invalid()
    repeat(coders.toInt()) {
        val flags = byte()
        val idLength = flags and 15
        if (idLength == 0 || flags and 0x80 != 0) invalid()
        skip(idLength.toLong())
        if (flags and 0x10 != 0 && (number() != 1L || number() != 1L)) invalid()
        if (flags and 0x20 != 0) skip(number())
    }
    repeat(coders.toInt() - 1) {
        if (number() >= coders || number() >= coders) invalid()
    }
    expect(0x0c)
    repeat(coders.toInt()) {
        if (number() > 8L * 1024 * 1024) invalid()
    }
}
