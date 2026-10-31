package com.pelican.comictv.sources

import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FileProviderIdentityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun eachAuthorityResolvesToItsOwnComponent() {
        val classes = listOf("explorer", "extensions", "updates").map { suffix ->
            requireNotNull(context.packageManager.resolveContentProvider("${context.packageName}.$suffix", PackageManager.GET_META_DATA)).name
        }
        assertEquals(classes.distinct(), classes)
    }

    @Test fun explorerAndExtensionUrisReadOnlyTheirOwnDirectories() {
        assertProviderReads("extensions", File(context.cacheDir, "extensions"))
        assertProviderReads("explorer", File(context.cacheDir, "explorer-installs"))
    }

    private fun assertProviderReads(suffix: String, directory: File) {
        val authority = "${context.packageName}.$suffix"
        directory.mkdirs()
        val inside = File(directory, "provider-${System.nanoTime()}.apk").apply { writeText(suffix) }
        val outside = File(context.cacheDir, "outside-${System.nanoTime()}.apk").apply { writeText("private") }
        try {
            val uri = FileProvider.getUriForFile(context, authority, inside)
            assertEquals(authority, uri.authority)
            assertEquals(suffix, context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            try { FileProvider.getUriForFile(context, authority, outside); fail("Outside file exposed") }
            catch (_: IllegalArgumentException) {}
        } finally { inside.delete(); outside.delete() }
    }
}
