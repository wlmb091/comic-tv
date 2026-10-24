package com.pelican.comictv.backup

import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
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
}
