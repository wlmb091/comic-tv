package com.pelican.comictv.library

import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.storage.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class StorageSafetyTest {
    @Test fun trustedAncestorAliasWorksButChildAndRootLinksAreNeverFollowed() {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "storage-safety-${UUID.randomUUID()}").apply { mkdirs() }
        val direct = File(root, "direct").apply { mkdirs() }
        val alias = File(root, "alias")
        val rootLink = File(root, "redirected-pages")
        val link = File(direct, "pages/link")
        try {
            val original = File(direct, "originals/keep").apply { parentFile.mkdirs(); writeBytes(ByteArray(9)) }
            val keep = File(root, "outside/keep").apply { parentFile.mkdirs(); writeBytes(ByteArray(17)) }
            File(direct, "pages/page").apply { parentFile.mkdirs(); writeBytes(ByteArray(5)) }
            Os.symlink(direct.absolutePath, alias.absolutePath)
            Os.symlink(keep.parentFile!!.absolutePath, link.absolutePath)
            Os.symlink(keep.parentFile!!.absolutePath, rootLink.absolutePath)
            val storage = LibraryStorage(File(alias,"originals"), File(alias,"covers"), File(alias,"pages"), File(alias,"thumbs"))
            assertEquals(5L, storage.report().pages)
            val clean = storage.clear(CacheSelection.Both)
            assertEquals(5L, clean.freedBytes); assertTrue(clean.failures > 0)
            val rejected = LibraryStorage(File(direct,"originals"), File(direct,"covers"), rootLink, File(direct,"thumbs"))
            assertTrue(rejected.clear(CacheSelection.Pages).failures > 0)
            assertTrue(original.isFile); assertTrue(keep.isFile)
        } finally {
            link.delete(); rootLink.delete(); alias.delete()
            root.deleteRecursively()
        }
    }
}
