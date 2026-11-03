package com.pelican.comictv.tv

import org.junit.Assert.*
import org.junit.Test

class StremioProtocolTest {
    @Test fun configuredLinksPreserveSecretsAndQueries() {
        assertEquals("https://example.org/a/token/manifest.json?key=secret", StremioLinks.manifest("stremio://example.org/a/token/manifest.json?key=secret"))
        assertEquals("https://example.org/base/manifest.json", StremioLinks.manifest("https://example.org/base/"))
        assertEquals("https://example.org/a/token/meta/series/a%2Fb%3A1.json?key=secret", StremioLinks.resource("https://example.org/a/token/manifest.json?key=secret", "meta", "series", "a/b:1"))
    }
    @Test fun nonHttpLinksAreRejected() {
        for (s in listOf("file:///etc/passwd", "javascript:alert(1)", "https://user:pass@example.org/manifest.json", "https://example.org/manifest.json#secret", "https://example.org/\u0009manifest.json")) {
            assertTrue(runCatching { StremioLinks.manifest(s) }.isFailure)
        }
    }
    @Test fun configureUsesFinalPathAndRetainsQuery() {
        assertEquals("https://example.org/manifest.json-token/configure?key=a%2Fb", StremioLinks.configure("https://example.org/manifest.json-token/manifest.json?key=a%2Fb"))
    }
    @Test fun resourceRejectsDotSegmentsAndUsesEscapedExtras() {
        assertTrue(runCatching { StremioLinks.resource("https://example.org/manifest.json", "meta", "movie", "..") }.isFailure)
        assertEquals("https://example.org/catalog/anime/a%2Fb/genre=Sci-Fi%20%26%20Fantasy&search=a%2Fb%3Ac.json", StremioLinks.resource("https://example.org/manifest.json", "catalog", "anime", "a/b", mapOf("search" to "a/b:c", "genre" to "Sci-Fi & Fantasy")))
    }
    @Test fun manifestsRequireProtocolArraysAndRejectInvalidCatalogs() {
        for (text in listOf("""{"id":"x","name":"X","version":"1.0.0"}""", """{"id":"x","name":"X","version":"1.0.0","types":[],"resources":"stream","catalogs":[]}""")) {
            assertTrue(runCatching { StremioJson.manifest(text) }.isFailure)
        }
        val m=StremioJson.manifest("""{"id":"x","name":"X","version":"1.0.0","types":["movie"],"resources":["catalog"],"catalogs":[{"type":"movie"},{"id":"ok","type":"movie"}]}""")
        assertEquals(listOf("ok"),m.catalogs.map{it.id})
    }
    @Test fun objectResourcesOverrideTopLevelPrefixes() {
        val addon = StremioJson.manifest("""{"id":"x","name":"X","version":"1.0.0","types":["movie"],"idPrefixes":["tt"],"resources":["meta",{"name":"stream","types":["anime"]}],"catalogs":[]}""")
        assertTrue(addon.supports("meta", "movie", "tt123"))
        assertFalse(addon.supports("meta", "movie", "kitsu:1"))
        assertTrue(addon.supports("stream", "anime", "kitsu:1"))
        assertFalse(addon.supports("stream", "movie", "tt123"))
    }
    @Test fun catalogRequiresSearchAndIdsStayOpaque() {
        val m=StremioJson.manifest("""{"id":"x","name":"X","version":"1.0.0","types":["anime"],"resources":["catalog"],"catalogs":[{"id":"my:list","type":"anime","extra":[{"name":"search","isRequired":true}]}]}""")
        assertFalse(m.catalogs.single().canRequest(emptyMap()))
        assertTrue(m.catalogs.single().canRequest(mapOf("search" to "Fate")))
        val item=StremioJson.metas("""{"metas":[{"id":"kitsu:12","type":"anime","name":"Anime"}]}""").single()
        assertEquals("kitsu:12",item.id)
    }
    @Test fun torrentReadsCannotCrossUnverifiedPieces() {
        assertEquals(listOf(4,5), TorrentReadPlanner.pieces(8192, 4096, 2048))
        assertEquals(0, TorrentReadPlanner.readable(8192,4096,2048){false})
        assertEquals(2048, TorrentReadPlanner.readable(8192,4096,2048){it==4})
        assertEquals(4096, TorrentReadPlanner.readable(8192,4096,2048){true})
        assertTrue(runCatching { TorrentReadPlanner.pieces(Long.MAX_VALUE,1,2048) }.isFailure)
        assertTrue(runCatching { TorrentReadPlanner.pieces((Int.MAX_VALUE.toLong()+1)*2048,1,2048) }.isFailure)
    }
    @Test fun streamsDoNotSilentlySelectFileZeroOnInvalidIndices() {
        val streams=StremioJson.streams("""{"streams":[{"infoHash":"0123456789012345678901234567890123456789","fileIdx":"bad"},{"infoHash":"0123456789012345678901234567890123456789","fileIdx":-1},{"url":"https://example.org/movie.mp4","behaviorHints":{"proxyHeaders":{"request":{"Referer":"https://example.org/"}}}}]}""")
        assertEquals(1,streams.size)
        assertEquals("https://example.org/",streams.single().headers["Referer"])
    }
    @Test fun duplicateCatalogEntriesAndVideosAreDeduplicated() {
        assertEquals(1,StremioJson.metas("""{"metas":[{"id":"x","type":"movie","name":"X"},{"id":"x","type":"movie","name":"X"}]}""").size)
        val m=StremioJson.meta("""{"meta":{"id":"x","type":"series","name":"X","videos":[{"id":"x:1:1","episode":1},{"id":"x:1:1","episode":1},{}]}}""")
        assertEquals(listOf("x:1:1"),m.videos.map{it.id})
    }
    @Test fun legacyExtrasAndDeclaredOptionsAreRespected() {
        val m=StremioJson.manifest("""{"id":"x","name":"X","version":"1.0.0","types":["movie"],"resources":["catalog"],"catalogs":[{"id":"search","type":"movie","extraSupported":["search"],"extraRequired":["search"]},{"id":"genre","type":"movie","extra":[{"name":"genre","options":["Action"]}]}]}""")
        assertFalse(m.catalogs.first().canRequest(emptyMap()))
        assertTrue(m.catalogs.first().canRequest(mapOf("search" to "Movie")))
        assertFalse(m.catalogs.last().canRequest(mapOf("genre" to "Other")))
    }
    @Test fun parseFailuresDoNotEchoSecretUrls() {
        val error=runCatching{StremioLinks.manifest("https://example.org/private-token/has a space")}.exceptionOrNull()
        assertNotNull(error)
        assertFalse(error!!.message.orEmpty().contains("private-token"))
    }
}
