package com.pelican.comictv.metadata

import org.junit.Assert.*
import org.junit.Test

class MetadataRepositoryTest {
    @Test fun workResultsRetainProvenanceAndMissingRating() {
        val works = MetadataParser.openLibrary("""{"docs":[{"key":"/works/OL1W","title":"Original","author_name":["Writer"],"isbn":["9780000000001"]},{"key":"https://evil.test","title":"bad"}]}""")
        assertEquals(1, works.size)
        assertEquals("https://openlibrary.org/works/OL1W", works.single().url)
        assertNull(works.single().rating)
        assertEquals("Writer", works.single().authors)
    }
    @Test fun rejectsAnimeAndInvalidScores() {
        val works = MetadataParser.aniList("""{"data":{"Page":{"media":[{"id":1,"type":"ANIME","title":{"romaji":"Film"}},{"id":2,"type":"MANGA","title":{"romaji":"Manga"},"averageScore":101,"description":"<script>bad()</script><b>Story</b>"}]}}}""")
        assertEquals(1, works.size)
        assertNull(works.single().rating)
        assertEquals("Story", works.single().synopsis)
    }
    @Test fun capsCandidatesAndChecksCount() {
        val docs = (1..20).joinToString(",") { """{"key":"/works/OL${it}W","title":"Work","ratings_average":4.5,"ratings_count":0}""" }
        val works = MetadataParser.openLibrary("""{"docs":[$docs]}""")
        assertEquals(6, works.size)
        assertNull(works.first().rating)
    }
}
