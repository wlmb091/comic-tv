package com.pelican.comictv.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserSitesTest {
    @Test fun onlySevenApprovedSitesAndEncodedSearchTerms() {
        assertEquals(7, BrowserSites.all.size)
        assertEquals(7, BrowserSites.all.map { it.id }.toSet().size)
        BrowserSites.all.forEach { site ->
            val uri = java.net.URI(site.searchUrl("Batman & Robin #1"))
            assertEquals("https", uri.scheme)
            assertFalse(uri.rawQuery.contains("#"))
            assertFalse(uri.rawQuery.contains(" "))
        }
    }

    @Test fun bloggerResultsKeepOnlyOwnArticlesAndLimitImages() {
        val site = BrowserSites.all.first { it.id == "renegados" }
        val html = """<div class="post"><h3 class="post-title"><a href="https://renegadoscomics.blogspot.com/2026/01/batman.html">Batman</a></h3><div class="post-body"><img src="https://blogger.googleusercontent.com/cover.jpg"></div></div>
            <div class="post"><h3 class="post-title"><a href="javascript:alert(1)">Bad</a></h3></div>"""
        val hits = BrowserSites.parseHtml(site, html)
        assertEquals(1, hits.size)
        assertEquals("Batman", hits.single().title)
        assertEquals("https://blogger.googleusercontent.com/cover.jpg", hits.single().cover)
    }

    @Test fun wordpressAndArchiveParseMetadataWithoutDownloadingBooks() {
        val sq = BrowserSites.all.first { it.id == "sq" }
        val posts = BrowserSites.parseJson(sq, """[{"title":"Batman &amp; Robin","url":"https://site.soquadrinhos.com/batman/"}]""")
        assertEquals("Batman & Robin", posts.single().title)
        val archive = BrowserSites.all.first { it.id == "archive" }
        val hits = BrowserSites.parseJson(archive, """{"response":{"docs":[{"identifier":"batman-1","title":"Batman"}]}}""")
        assertEquals("https://archive.org/details/batman-1", hits.single().url)
        assertTrue(hits.single().cover!!.startsWith("https://archive.org/services/img/"))
    }
}
