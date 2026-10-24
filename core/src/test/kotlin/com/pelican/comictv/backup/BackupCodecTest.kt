package com.pelican.comictv.backup

import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    @Test fun optionalPercentagePreservesLegacyBackupsAndAllNewSteps() {
        val legacy = BackupCodec.encode(document())
        assertFalse(legacy.toString(Charsets.UTF_8).contains("textPercent"))
        assertNull(BackupCodec.decode(legacy.inputStream()).appearance.textPercent)
        for (percent in 85..130 step 5) {
            val value = document().copy(appearance = BackupAppearance(textPercent = percent))
            assertEquals(value, BackupCodec.decode(BackupCodec.encode(value).inputStream()))
        }
        val valid = BackupCodec.encode(document().copy(appearance = BackupAppearance(textPercent = 100))).toString(Charsets.UTF_8)
        for (bad in listOf("84", "131", "101", "100.5", "\"100\"", "true")) {
            assertThrows(Exception::class.java) { BackupCodec.decode(valid.replace("\"textPercent\":100", "\"textPercent\":$bad").byteInputStream()) }
        }
    }
    private fun document() = BackupDocument(createdAt = 123, comics = listOf(BackupComic("local-one", "Órbita", 25,
        currentPage = 12, lastRead = 100, favorite = true, sha256 = "a".repeat(64))),
        collections = listOf(BackupCollection("sci-fi", "Ficção")), links = listOf(BackupLink("local-one", "sci-fi")))
    @Test fun roundTripPreservesReadingDataWithoutPageUrlsOrFiles() {
        val bytes = BackupCodec.encode(document())
        assertEquals(document(), BackupCodec.decode(bytes.inputStream()))
        assertFalse(bytes.toString(Charsets.UTF_8).contains("imageUrl"))
    }
    @Test fun strictTypesVersionDuplicatesAndReferencesAreRejected() {
        val json = BackupCodec.encode(document()).toString(Charsets.UTF_8)
        for (bad in listOf(json.replace("\"version\":1", "\"version\":99"),
            json.replace("\"favorite\":true", "\"favorite\":\"true\""),
            json.replace("\"version\":1", "\"version\":4294967297"),
            json.replace("\"currentPage\":12", "\"currentPage\":1.5"),
            json.replace("\"currentPage\":12", "\"currentPage\":-1"),
            json.replace("\"version\":1", "\"version\":1,\"version\":1"),
            json.replace("\"collectionId\":\"sci-fi\"", "\"collectionId\":\"missing\""),
            json.replace("local-one", "../escape"))) {
            assertThrows(Exception::class.java) { BackupCodec.decode(bad.byteInputStream()) }
        }
        assertThrows(Exception::class.java) { BackupCodec.decode(ByteArray(BackupCodec.MAX_BYTES + 1) { 32 }.inputStream()) }
    }
    @Test fun latestReadingWinsWithoutDroppingFavoritesAndPageIsClamped() {
        val current = document().comics.single().copy(lastRead = 200, favorite = false, currentPage = 20)
        val oldBackup = current.copy(lastRead = 100, currentPage = 2, favorite = true)
        val kept = mergeBackupComic(current, oldBackup)
        assertEquals(20, kept.currentPage); assertTrue(kept.favorite)
        assertEquals(kept, mergeBackupComic(kept, oldBackup))
        val newer = oldBackup.copy(lastRead = 300, currentPage = 24)
        assertEquals(9, mergeBackupComic(current.copy(pageCount = 10), newer).currentPage)
    }
    @Test fun restorePreflightRejectsAggregateCollectionOverflowAndPreservesExistingNames() {
        val existing = (0 until 2000).map { BackupCollection("c$it", "Coleção $it") }
        assertThrows(IllegalArgumentException::class.java) { mergeBackupCollections(existing, listOf(BackupCollection("new", "Nova"))) }
        val merged = mergeBackupCollections(existing, listOf(BackupCollection("same", "COLECAO 1")))
        assertEquals(2000, merged.first.size); assertEquals("c1", merged.second["same"])
    }
}
