package com.pelican.comictv.tv

import org.junit.Assert.*
import org.junit.Test

class TvSourceFilterTest {
    private fun s(name:String,addon:String="Torrentio",description:String="")=TvStream(name=name,description=description,addonName=addon,infoHash="a".repeat(40))

    @Test fun qualityIsReadFromTheName() {
        assertEquals("4K",SourceFilter.quality(s("Torrentio\n4k HDR")))
        assertEquals("4K",SourceFilter.quality(s("Filme.2160p.WEB-DL")))
        assertEquals("1080p",SourceFilter.quality(s("Filme 1080p BluRay")))
        assertEquals("720p",SourceFilter.quality(s("x","Brazuca","Filme.720p.HDTV")))
        assertEquals("Outras",SourceFilter.quality(s("Filme CAM")))
    }
    @Test fun audioGroupsTheUsualSpellings() {
        assertEquals("Dual áudio",SourceFilter.audio(s("Filme 1080p DUAL")))
        assertEquals("Dual áudio",SourceFilter.audio(s("Filme Dual-Áudio")))
        assertEquals("Dual áudio",SourceFilter.audio(s("Filme multi audio")))
        assertEquals("Dublado",SourceFilter.audio(s("Filme Dublado 720p")))
        assertEquals("Dublado",SourceFilter.audio(s("Filme.PT-BR.1080p")))
        assertEquals("Dublado",SourceFilter.audio(s("Filme 1080p BR")))
        assertEquals("Dublado",SourceFilter.audio(s("Filme 🇧🇷")))
        assertEquals("Dublado",SourceFilter.audio(s("Filme nacional")))
        assertEquals("Inglês",SourceFilter.audio(s("Filme 1080p BRRip")))
        assertEquals("Inglês",SourceFilter.audio(s("Filme 1080p BluRay")))
        assertEquals("Inglês",SourceFilter.audio(s("Filme english")))
    }
    @Test fun filtersCombineAndCountWhatTheOthersLeave() {
        val list=listOf(s("A 1080p Dublado"),s("B 720p"),s("C 1080p","Brazuca"),s("D 4k dual","Brazuca"))
        val r=SourceFilter.apply(list,SourceFilter.Choice(addon="Brazuca",quality="1080p"))
        assertEquals(listOf(2),r.shown.map{it.index})
        assertEquals(listOf("Todas" to 2,"Torrentio" to 1,"Brazuca" to 1),r.addons.map{it.label to it.count})
        assertEquals(2,r.qualities.first().count)
        assertEquals(1,r.audios.first{it.value=="Inglês"}.count)
    }
    @Test fun aRememberedAddonMissingFromTheTitleShowsEverything() {
        val r=SourceFilter.apply(listOf(s("A"),s("B")),SourceFilter.Choice(addon="Outro"))
        assertEquals(2,r.shown.size)
        assertEquals(SourceFilter.ALL,r.applied.addon)
    }
    @Test fun magnetJoinsAddonAndPublicTrackers() {
        val hash="0123456789abcdef0123456789abcdef01234567"
        val link=TorrentLinks.magnet("",hash,listOf("tracker:udp://a.example:1/announce","dht:$hash","tracker:javascript:x"),true)!!
        assertTrue(link.startsWith("magnet:?xt=urn:btih:$hash&tr=udp%3A%2F%2Fa.example%3A1%2Fannounce"))
        assertEquals(1+TorrentLinks.PUBLIC_TRACKERS.size,Regex("&tr=").findAll(link).count())
        assertFalse(link.contains("javascript"))
        assertEquals(1,Regex("&tr=").findAll(TorrentLinks.magnet("",hash,listOf("tracker:udp://a.example:1/announce"),false)!!).count())
        assertNull(TorrentLinks.magnet("","xyz",emptyList(),true))
        val existing="magnet:?xt=urn:btih:$hash&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
        assertEquals(TorrentLinks.PUBLIC_TRACKERS.size,Regex("&tr=").findAll(TorrentLinks.magnet(existing,"",emptyList(),true)!!).count())
    }
}
