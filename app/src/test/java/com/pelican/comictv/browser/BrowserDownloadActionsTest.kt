package com.pelican.comictv.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserDownloadActionsTest {
    private val row = BrowserDownloadItem("00000000-0000-0000-0000-000000000001", "https://example.org/book", "book.epub", "A", status = BrowserDownloadStatus.Complete)
    @Test fun primaryControlPersistsThroughImportAndBecomesRead() {
        val before = browserDownloadActions(row, false).first()
        val during = browserDownloadActions(row.copy(status = BrowserDownloadStatus.Importing), false).first()
        val after = browserDownloadActions(row.copy(status = BrowserDownloadStatus.Imported, importedId = "book-id"), false).first()
        assertEquals("primary", before.key); assertEquals(before.key, during.key); assertEquals(before.key, after.key)
        assertEquals("import", before.command); assertNull(during.command)
        assertEquals("read", after.command); assertEquals("Ler agora", after.label)
    }
    @Test fun everyStatusHasPersistentPrimaryAndFailedImportRemainsRetryable() {
        BrowserDownloadStatus.entries.forEach { status ->
            assertEquals("primary", browserDownloadActions(row.copy(status = status), false).first().key)
        }
        assertEquals("import", browserDownloadActions(row.copy(error = "Falha na importação"), false).first().command)
    }
    @Test fun exportingKeepsSaveControlPresentWithoutStartingAnotherExport() {
        val before = browserDownloadActions(row, false).first { it.key == "save" }
        val during = browserDownloadActions(row, true).first { it.key == "save" }
        assertEquals(before.key, during.key); assertNull(during.command)
    }
}
