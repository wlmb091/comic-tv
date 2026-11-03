package com.pelican.comictv.tv

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SubtitleMemoryTest {
    private fun srt(n:Int)="1\n00:00:01,000 --> 00:00:02,000\nlinha $n\n"

    @Test fun keepsFivePerVideoNewestFirstAndForgetsDroppedChoice() {
        val root=Files.createTempDirectory("sub-memory").toFile()
        val m=SubtitleMemory(root)
        val first=m.save("tt1|tt1","Arquivo 1","pob",srt(1),SubtitleText.Kind.Srt)
        m.choose("tt1|tt1",first,1200)
        for(i in 2..6)m.save("tt1|tt1","Arquivo $i","",srt(i),SubtitleText.Kind.Srt)
        val list=m.list("tt1|tt1")
        assertEquals(5,list.size)
        assertEquals("Arquivo 6",list.first().label)
        assertTrue(list.none { it.id==first })
        assertNull(m.choice("tt1|tt1"))
        root.deleteRecursively()
    }

    @Test fun sameTextBecomesNewestWithoutDuplicateAndChoiceKeepsDelay() {
        val root=Files.createTempDirectory("sub-memory").toFile()
        val m=SubtitleMemory(root)
        val a=m.save("show|ep1","A","",srt(1),SubtitleText.Kind.Srt)
        m.save("show|ep1","B","",srt(2),SubtitleText.Kind.Srt)
        assertEquals(a,m.save("show|ep1","A de novo","",srt(1),SubtitleText.Kind.Srt))
        assertEquals(listOf("A de novo","B"),m.list("show|ep1").map { it.label })
        m.choose("show|ep1",a,-99_999)
        assertEquals(SubtitleMemory.Choice(a,-60_000),m.choice("show|ep1"))
        assertEquals(srt(1),m.list("show|ep1").first().file.readText())
        m.choose("show|ep1","off")
        assertEquals("off",m.choice("show|ep1")?.id)
        m.choose("show|ep1",null)
        assertNull(m.choice("show|ep1"))
        assertTrue(m.list("show|ep2").isEmpty())
        root.deleteRecursively()
    }

    @Test fun forgettingAnUnknownVideoCreatesNothing() {
        val root=Files.createTempDirectory("sub-memory").toFile()
        SubtitleMemory(root).choose("never|played",null)
        assertTrue(root.listFiles().isNullOrEmpty())
        root.deleteRecursively()
    }
}
