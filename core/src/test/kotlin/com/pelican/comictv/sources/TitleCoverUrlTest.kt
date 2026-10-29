package com.pelican.comictv.sources

import org.junit.Assert.*
import org.junit.Test

class TitleCoverUrlTest {
    @Test fun legacySourceCoverIsUpgradedToTls() {
        assertEquals("https://static.hq-now.com/hqs/hqs/uploads/picture/image/398994/00Front_Cover_06.jpg",
            normalizeTitleCoverUrl("http://static.hq-now.com/hqs/hqs/uploads/picture/image/398994/00Front_Cover_06.jpg"))
    }
    @Test fun signedPathAndQueryBytesArePreserved() {
        assertEquals("https://example.org/a%2fb%20c.jpg?sig=x%2By%2Fz%3D&name=a+b&empty=",
            normalizeTitleCoverUrl("http://example.org/a%2fb%20c.jpg?sig=x%2By%2Fz%3D&name=a+b&empty="))
    }
    @Test fun defaultHttpPortChangesToHttpsDefaultAndOtherPortsRemainExplicit() {
        assertEquals("https://example.org/a", normalizeTitleCoverUrl("http://example.org:80/a"))
        assertEquals("https://example.org/a", normalizeTitleCoverUrl("http://example.org:00080/a"))
        assertEquals("https://example.org:8443/a", normalizeTitleCoverUrl("http://example.org:8443/a"))
        assertEquals("https://example.org:8443/a", normalizeTitleCoverUrl("https://example.org:8443/a"))
        assertEquals("https://[::1]/a", normalizeTitleCoverUrl("http://[::1]:80/a"))
    }
    @Test fun existingHttpsRemainsUnchanged() {
        val url = "https://example.org:443/a%2Fb.jpg?sig=abc%3D"
        assertEquals(url, normalizeTitleCoverUrl(url))
    }
    @Test fun malformedAndCredentialedUrlsAreRejected() {
        listOf(null, "", "/cover.jpg", "file:///cover.jpg", "javascript:alert(1)", "ftp://example.org/a",
            "https:///cover.jpg", "https://user:pass@example.org/a", "http://@example.org/a",
            "https://example.org:0/a", "https://example.org:65536/a", "https://example.org/%xx",
            " https://example.org/a", "https://example.org/a\n").forEach { assertNull(it, normalizeTitleCoverUrl(it)) }
    }
}
