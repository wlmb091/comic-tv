package com.pelican.comictv.tv

import org.junit.Assert.*
import org.junit.Test

class TvSubtitlesTest {
    @Test fun windows1252FilesKeepPortugueseAccents() {
        val bytes="1\n00:00:01,000 --> 00:00:02,000\nAção, coração e você\n".toByteArray(charset("windows-1252"))
        assertTrue(SubtitleText.decode(bytes).contains("Ação, coração e você"))
    }
    @Test fun utf8AndBomAreRecognized() {
        val text="WEBVTT\n\n00:01.000 --> 00:02.000\nOlá"
        assertEquals(text,SubtitleText.decode(text.toByteArray()))
        assertEquals(text,SubtitleText.decode(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte())+text.toByteArray()))
        assertEquals(text,SubtitleText.decode(byteArrayOf(0xFF.toByte(),0xFE.toByte())+text.toByteArray(Charsets.UTF_16LE)))
    }
    @Test fun formatsAreDetectedAndInvalidFilesRejected() {
        assertEquals(SubtitleText.Kind.Vtt,SubtitleText.kind("﻿WEBVTT\n"))
        assertEquals(SubtitleText.Kind.Ass,SubtitleText.kind("[Script Info]\nTitle: x\n"))
        assertEquals(SubtitleText.Kind.Srt,SubtitleText.kind("1\n00:00:01,000 --> 00:00:02,000\nx"))
        assertTrue(runCatching { SubtitleText.validate("<html><body>404</body></html>") }.isFailure)
        assertEquals(SubtitleText.Kind.Srt,SubtitleText.validate("1\n00:00:01,000 --> 00:00:02,000\nx"))
    }
    @Test fun srtAndVttShiftBothEndsAndClampAtZero() {
        val srt=SubtitleText.shift("1\n00:00:01,500 --> 00:00:03,000\n12:00 não muda",SubtitleText.Kind.Srt,-2000)
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\n12:00 não muda",srt)
        val vtt=SubtitleText.shift("WEBVTT\n\n00:59.500 --> 01:00.250 line:90%\nx",SubtitleText.Kind.Vtt,750)
        assertEquals("WEBVTT\n\n00:01:00.250 --> 00:01:01.000 line:90%\nx",vtt)
    }
    @Test fun assShiftKeepsStylesAndTextWithCommas() {
        val line="Dialogue: 0,0:00:01.50,0:00:04.00,Default,,0,0,0,,{\\pos(10,20)}Olá, mundo"
        assertEquals("Dialogue: 0,0:00:02.75,0:00:05.25,Default,,0,0,0,,{\\pos(10,20)}Olá, mundo",
            SubtitleText.shift("[Events]\n$line",SubtitleText.Kind.Ass,1250).lines()[1])
        assertEquals("Style: Default,Arial,20",SubtitleText.shift("Style: Default,Arial,20",SubtitleText.Kind.Ass,1000))
    }
    @Test fun languagesRankBrazilThenPortugueseThenEnglish() {
        assertEquals(0,SubtitleLanguages.rank("pob","",true))
        assertEquals(1,SubtitleLanguages.rank("por","",true))
        assertEquals(0,SubtitleLanguages.rank("por","",false))
        assertEquals(1,SubtitleLanguages.rank("pt-BR","",false))
        assertEquals(2,SubtitleLanguages.rank("eng","",true))
        assertEquals(Int.MAX_VALUE,SubtitleLanguages.rank("spa","",true))
        assertEquals("Português (Brasil)",SubtitleLanguages.name("pob"))
    }
    @Test fun streamsKeepDirectLinksFirstAndSortTorrentsBySeeders() {
        val few=TvStream("Torrentio","1080p\n👤 7 💾 3.1 GB",infoHash="a".repeat(40))
        val many=TvStream("Torrentio","720p\n👤 116 💾 1 GB",infoHash="b".repeat(40))
        val unknown=TvStream("Outro","sem dados",infoHash="c".repeat(40))
        val direct=TvStream("Direto","1080p",url="https://example.org/v.mp4")
        assertEquals(listOf(direct,many,few,unknown),StreamRank.sorted(listOf(few,unknown,many,direct)))
        assertTrue(StreamRank.slow(few));assertFalse(StreamRank.slow(many));assertFalse(StreamRank.slow(unknown))
        assertEquals(42,StreamRank.seeders(TvStream("x","Seeders: 42")))
    }
    @Test fun addonSubtitlesKeepOnlyHttpLinks() {
        val list=StremioJson.subtitles("""{"subtitles":[{"id":"1","url":"https://s.example/a.srt","lang":"pob"},{"id":"2","url":"file:///etc/passwd","lang":"por"},{"url":"https://s.example/b.vtt","lang":"eng"}]}""")
        assertEquals(listOf("https://s.example/a.srt","https://s.example/b.vtt"),list.map{it.url})
        val streams=StremioJson.streams("""{"streams":[{"url":"https://v.example/a.mp4","subtitles":[{"id":"s","url":"https://s.example/c.ass","lang":"por"}]}]}""")
        assertEquals("https://s.example/c.ass",streams.single().subtitles.single().url)
    }
    @Test fun prefsAreNormalized() {
        val p=TvVideoPrefs("bad",TvSubtitlePrefs(sizePercent=999,color=9,background=-1,edge=7,bottomPercent=50)).normalized()
        assertEquals("internal",p.player);assertEquals(200,p.subtitles.sizePercent);assertEquals(3,p.subtitles.color)
        assertEquals(0,p.subtitles.background);assertEquals(2,p.subtitles.edge);assertEquals(30,p.subtitles.bottomPercent)
    }
}
