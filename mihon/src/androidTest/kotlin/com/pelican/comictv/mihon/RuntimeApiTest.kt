package com.pelican.comictv.mihon

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.source.CatalogueSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Host API smoke check; real installed APK load additionally requires a trusted fixture/device. */
@RunWith(AndroidJUnit4::class)
class RuntimeApiTest {
    @Test fun runtimeInitializesBeforeLoadingAndExposes16Bridge() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MihonEngine(context)
        val methods = CatalogueSource::class.java.methods.map { it.name }
        assertTrue(methods.contains("getMangaUpdate"))
        assertTrue(methods.contains("fetchPopularManga"))
        assertEquals(CatalogueSource::class.java, context.classLoader.loadClass("eu.kanade.tachiyomi.source.CatalogueSource"))
    }
}
