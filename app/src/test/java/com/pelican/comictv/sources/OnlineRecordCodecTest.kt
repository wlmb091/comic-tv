package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class OnlineRecordCodecTest {
    private val record = OnlineReadingRecord(SourceHandle("pkg", 3, "HQ", "pt", "https://hq.example"), OnlineTitle("title/1", "A saga"), OnlineChapter("chapter/1", "Parte 1"), "ab".repeat(32), listOf(OnlinePage(0, "page/1", "https://image.example/1?token=old")))
    private val id = OnlineIdentity.id(record.source, record.title, record.chapter)
    @Test fun persistenceRetainsSourceChapterPinAndPageMetadataForResume() {
        assertEquals(record, OnlineRecordCodec.decode(id, OnlineRecordCodec.encode(record)))
    }
    @Test fun recordCannotBeSubstitutedForAnotherChapter() {
        assertThrows(IllegalArgumentException::class.java) { OnlineRecordCodec.decode(id, OnlineRecordCodec.encode(record.copy(chapter = record.chapter.copy(key = "chapter/2")))) }
    }
    @Test fun emptyOrExcessiveChaptersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { OnlineRecordCodec.requirePages(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { OnlineRecordCodec.requirePages(List(5001) { OnlinePage(it, "page/$it") }) }
    }
    @Test fun invalidPinCannotBeRestoredAsTrustedMetadata() {
        assertThrows(IllegalArgumentException::class.java) { OnlineRecordCodec.decode(id, OnlineRecordCodec.encode(record.copy(pin = "bad"))) }
    }
    @Test fun oversizedRecordsAreRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) { OnlineRecordCodec.decode(id, ByteArray(OnlineRecordCodec.MAX_BYTES + 1)) }
    }
}
