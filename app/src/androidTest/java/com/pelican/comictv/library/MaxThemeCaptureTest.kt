package com.pelican.comictv.library

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pelican.comictv.ui.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic data/art are generated in the test process only; never packaged in the app. */
@OptIn(ExperimentalTestApi::class)
class MaxThemeCaptureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureMaxHomeCardDockAndScrolledRow() = captureScenario(100, false, "max")
    @Test fun captureMaxAt130Percent() = captureScenario(130, false, "max-130")
    @Test fun captureMaxHighContrast() = captureScenario(100, true, "max-high-contrast")

    private fun captureScenario(percent: Int, contrast: Boolean, folder: String) = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        val scratch = File(real.cacheDir, "max-capture-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(real) {
            override fun getFilesDir() = File(scratch, "files").apply { mkdirs() }
            override fun getCacheDir() = File(scratch, "cache").apply { mkdirs() }
        }
        val database = Room.inMemoryDatabaseBuilder(real, LibraryDatabase::class.java).build()
        val repository = LibraryRepository(context, database.comics(), collectionDao = database.collections())
        val books = (0..7).map { ComicEntity("capture-$it", "Obra de teste ${it + 1}", 25,
            currentPage = 3 + it, lastRead = 100L - it) }
        var shown by mutableStateOf(true)
        val outputs = File(real.filesDir, "theme-results/$folder").apply { mkdirs() }
        val imageReady = AtomicBoolean(contrast)
        try {
            books.forEachIndexed { index, comic ->
                database.comics().insert(comic)
                val bitmap = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, 480f, 720f,
                        intArrayOf(0xff282050.toInt() + index * 0x00080402, 0xff111126.toInt()), null, Shader.TileMode.CLAMP)
                }
                canvas.drawPaint(paint)
                paint.shader = null; paint.color = android.graphics.Color.WHITE; paint.textSize = 46f
                canvas.drawText("TESTE ${index + 1}", 30f, 110f, paint)
                repository.cover(comic.id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                bitmap.recycle()
            }
            val art = File(scratch, "test-backdrop.png")
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawPaint(Paint().apply { shader = LinearGradient(0f, 1080f, 1920f, 0f,
                intArrayOf(0xff100b20.toInt(), 0xff593e86.toInt(), 0xffa088bf.toInt()), null, Shader.TileMode.CLAMP) })
            art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            repository.importCinemaArtwork(books.first().id, Uri.fromFile(art))
            compose.setContent {
                ComicTvTheme(AppearanceOptions(preset = ColorPreset.Max, textPercent = percent, highContrast = contrast)) {
                    if (shown) {
                        var initial by remember { mutableStateOf(true) }
                        var selected by remember { mutableStateOf(books.first().id) }
                        CompositionLocalProvider(LocalShellLibrarySearch provides {}) {
                            TvShell("library", {}, backdrop = {
                                val artworkId = selected
                                CinemaBackdrop(artworkId, repository, Modifier.fillMaxSize(),
                                    onImageReady = { if (artworkId == selected) imageReady.set(true) })
                            }) {
                                LibraryScreen(books, repository, null, {
                                    if (selected != it) { imageReady.set(contrast); selected = it }
                                }, {}, {},
                                    requestInitialFocus = initial, onInitialFocus = { initial = false })
                            }
                        }
                    }
                }
            }
            compose.onNodeWithText("Continuar leitura").assertIsDisplayed().assertIsFocused()
            compose.waitUntil(30_000) { imageReady.get() }
            compose.mainClock.advanceTimeBy(300)
            compose.onNodeWithTag("max-explore").assertExists()
            capture(outputs, "home", imageReady)
            compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-recent-capture-0"))
            val first = compose.onNodeWithTag("library-recent-capture-0")
            first.performSemanticsAction(SemanticsActions.RequestFocus) { it() }.assertIsFocused()
            capture(outputs, "card-focused", imageReady)
            compose.onNodeWithTag("nav-library").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                .assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
            compose.onAllNodes(isFocused()).assertCountEquals(1)
            compose.onNodeWithTag("nav-library").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            capture(outputs, "navigation-focused", imageReady)
            first.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                .performKeyInput { repeat(5) { pressKey(Key.DirectionRight) } }
            compose.onNodeWithTag("library-recent-capture-5").assertIsDisplayed().assertIsFocused()
            capture(outputs, "row-scrolled", imageReady)
        } finally {
            compose.runOnIdle { shown = false }; compose.waitForIdle()
            repository.closeBackgroundWork(); database.close(); scratch.deleteRecursively()
        }
    }

    private fun capture(directory: File, name: String, imageReady: AtomicBoolean) {
        compose.waitUntil(30_000) { imageReady.get() }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        assertTrue(image.width > image.height)
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources
        File(directory, "display.txt").writeText("capturePx=${image.width}x${image.height}; " +
            "densityDpi=${config.displayMetrics.densityDpi}; appDp=${config.configuration.screenWidthDp}x${config.configuration.screenHeightDp}; " +
            "fontScale=${config.configuration.fontScale}; synthetic fixtures, not user library")
    }
}
