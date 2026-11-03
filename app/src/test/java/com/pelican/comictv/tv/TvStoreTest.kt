package com.pelican.comictv.tv

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class TvStoreTest {
    @Test fun repeatedAtomicWritesCanBeLoadedAndConfiguredUrlsRemainPrivateState() {
        val dir=Files.createTempDirectory("tv-store").toFile()
        try {
            val store=TvStore(dir)
            val addon=TvAddon("https://example.org/user-token/manifest.json?key=private",AddonManifest("x","X","1.0.0",types=listOf("movie"),resources=listOf(AddonResource("stream"))))
            store.save(TvSnapshot(addons=listOf(addon),cacheGiB=2))
            store.save(TvSnapshot(addons=listOf(addon),cacheGiB=4))
            assertEquals(4,store.load().cacheGiB)
            assertEquals(addon,store.load().addons.single())
            assertFalse(dir.resolve("state.tmp").exists())
        } finally {dir.deleteRecursively()}
    }

    @Test fun missingAndNullPersistedFieldsUseSafeDefaults() {
        val dir=Files.createTempDirectory("tv-store-missing").toFile()
        try {
            val file=dir.resolve("state.json")
            val store=TvStore(dir)
            file.writeText("""{"addons":[],"progress":[]}""")
            assertEquals(TvSnapshot(),store.load())
            file.writeText("""{"addons":[],"progress":[],"favorites":null,"cacheGiB":0}""")
            assertEquals(TvSnapshot(),store.load())
            file.writeText("""{"addons":null,"progress":null,"favorites":null}""")
            assertEquals(TvSnapshot(),store.load())
        } finally {dir.deleteRecursively()}
    }

    @Test fun invalidPersistedAddonCannotCrashTheNextCatalogRead() {
        val dir=Files.createTempDirectory("tv-store-corrupt").toFile()
        try {
            dir.resolve("state.json").writeText("""{"addons":[{"url":"https://example.org/manifest.json","manifest":{"id":"x","name":"X","version":"1","types":null,"resources":null}}],"favorites":[],"progress":[],"cacheGiB":8}""")
            assertEquals(TvSnapshot(),TvStore(dir).load())
        } finally {dir.deleteRecursively()}
    }
}
