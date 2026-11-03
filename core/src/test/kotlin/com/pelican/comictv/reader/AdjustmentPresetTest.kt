package com.pelican.comictv.reader

import com.pelican.comictv.backup.BackupCodec
import com.pelican.comictv.backup.BackupDocument
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class AdjustmentPresetTest {
    private val tones = ImageAdjustments(List(ImageAdjustments.SIZE) { if (it < 3) 20 else 0 })
    private val current = ImageAdjustments(List(ImageAdjustments.SIZE) { if (it < 2) 5 else 0 })
    @Test fun withPaperReplacesEverything() {
        assertEquals(tones, AdjustmentPreset("a", "HQ", values = tones.encode()).over(current))
    }
    @Test fun withoutPaperKeepsTheEditionsTemperatureAndTint() {
        val out = AdjustmentPreset("a", "HQ", values = tones.encode(), paper = false).over(current).values
        assertEquals(listOf(5, 5, 20), out.take(3))
    }
    @Test fun parseRefusesWhatIsNotAPreset() {
        assertNull(AdjustmentPreset.parse("{"))
        assertNull(AdjustmentPreset.parse("""{"name":"sem id"}"""))
        assertNull(AdjustmentPreset.parse("""{"id":"a/b","name":"x","values":"${tones.encode()}"}"""))
        assertNull(AdjustmentPreset.parse("""{"id":"a","name":" ","values":"${tones.encode()}"}"""))
        assertNull(AdjustmentPreset.parse("""{"id":"a","name":"x","values":"1:1,2"}"""))
        assertEquals("x", AdjustmentPreset.parse("""{"id":"a","name":"x","folder":"","values":"${tones.encode()}","paper":false}""")?.name)
    }
    @Test fun mergeKeepsWhatTheTvAlreadyHas() {
        val mine = AdjustmentPreset("a", "Meu", values = tones.encode())
        val merged = AdjustmentPreset.merge(listOf(mine), listOf(mine.copy(name = "Antigo"), mine.copy(id = "b", name = "Novo")))
        assertEquals(listOf("Meu", "Novo"), merged.map { it.name })
    }
    @Test fun presetsTravelInTheBackupOnlyWhenThereAreAny() {
        val empty = BackupDocument(createdAt = 1, comics = emptyList())
        assertFalse(BackupCodec.encode(empty).toString(Charsets.UTF_8).contains("imagePresets"))
        val preset = AdjustmentPreset("a", "HQ", "Antigas", tones.encode(), false)
        val back = BackupCodec.decode(BackupCodec.encode(empty.copy(imagePresets = listOf(preset))).inputStream())
        assertEquals(listOf(preset), back.imagePresets)
        assertThrows(Exception::class.java) { BackupCodec.encode(empty.copy(imagePresets = listOf(preset, preset))) }
    }
    @Test fun aSavedDrawingKeepsItsStepsAndADiscardedSessionLeavesNoTrace() {
        val root = Files.createTempDirectory("replay").toFile()
        try {
            val store = PageDrawingStore(File(root, "drawings"))
            fun step(n: Int) = ReplayStep(n, 0, 1, 1, 10, 10, byteArrayOf(n.toByte()))
            store.recordStep("c", 2, step(1))
            assertFalse(store.hasReplay("c", 2))
            store.commitReplay("c", 2)
            assertTrue(store.hasReplay("c", 2))
            store.recordStep("c", 2, step(2))
            store.discardReplay("c", 2)
            store.commitReplay("c", 2)
            store.recordStep("c", 2, step(3))
            store.commitReplay("c", 2)
            val seen = arrayListOf<Int>()
            store.readReplay("c", 2) { seen += it.x; true }
            assertEquals(listOf(1, 3), seen)
            assertEquals(2, store.replayCount("c", 2))
            assertTrue(store.backupFiles().none { it.first.contains("replay") || it.first.contains(".rec") })
            store.delete("c", 2)
            assertFalse(store.hasReplay("c", 2))
        } finally { root.deleteRecursively() }
    }
}
