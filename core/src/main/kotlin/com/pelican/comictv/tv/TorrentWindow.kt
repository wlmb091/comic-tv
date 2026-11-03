package com.pelican.comictv.tv

/** Original, bounded download-window policy: at most 24 MiB rounded up to whole torrent pieces. */
object TorrentWindow {
    const val AHEAD_BYTES = 24L * 1024 * 1024
    fun range(offset: Long, size: Long, pieceSize: Int, requested: Int): IntRange {
        require(offset >= 0 && size > 0 && pieceSize in 16_384..16*1024*1024)
        require(offset <= Long.MAX_VALUE-size)
        val first = offset/pieceSize
        val last = (offset+size-1)/pieceSize
        require(last <= Int.MAX_VALUE && requested.toLong() in first..last)
        val count = (AHEAD_BYTES+pieceSize-1)/pieceSize
        return requested..minOf(last,requested.toLong()+count-1).toInt()
    }
}
