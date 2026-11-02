package com.pelican.comictv.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pelican.comictv.transfer.CompanionStroke
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** A stroke the phone is still drawing: a preview that the finished rectangle replaces when the finger lifts. */
internal data class LiveStroke(val page: Int, val eraser: Boolean, val color: Color, val size: Float, val opacity: Float, val points: List<Float>)

internal val LocalPageDrawings = staticCompositionLocalOf<PageDrawings?> { null }

private const val LAYER_LONG_SIDE = 1600

/** The layer over the pages of one edition. Bitmaps change only on the main thread. */
internal class PageDrawings(private val store: PageDrawingStore, private val comicId: String, private val scope: CoroutineScope, private val io: CoroutineDispatcher) {
    private val layers = object : LinkedHashMap<Int, Bitmap?>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap?>) = size > 6
    }
    private val loads = mutableMapOf<Int, Job>()
    private var liveReset: Job? = null
    var version by mutableIntStateOf(0)
        private set
    var live by mutableStateOf<LiveStroke?>(null)
        private set
    var hidden by mutableStateOf(runCatching { store.hidden }.getOrDefault(false))
        private set

    fun toggleHidden() {
        hidden = !hidden
        val value = hidden
        scope.launch(io) { runCatching { store.hidden = value } }
    }
    fun peek(page: Int): Bitmap? = layers[page]

    private fun decode(page: Int, pageWidth: Int, pageHeight: Int): Bitmap? {
        val file = store.file(comicId, page) ?: return null
        // A page the source replaced by another shape would have the drawing out of place.
        store.pageSize(comicId, page)?.let { (width, height) ->
            if (pageWidth > 0 && pageHeight > 0 && abs(width.toFloat() / height - pageWidth.toFloat() / pageHeight) > .02f * pageWidth / pageHeight) return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > LAYER_LONG_SIDE) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample; inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }
    /** Loads a page's saved layer once; the page size guards against an image that changed. */
    fun request(page: Int, pageWidth: Int, pageHeight: Int) {
        if (layers.containsKey(page) || loads.containsKey(page)) return
        loads[page] = scope.launch {
            val bitmap = try { withContext(io) { runCatching { decode(page, pageWidth, pageHeight) }.getOrNull() } } finally { loads.remove(page) }
            layers[page] = bitmap
            version++
        }
    }
    fun reload(page: Int) {
        loads.remove(page)?.cancel()
        layers.remove(page)
        if (live?.page == page) live = null
        version++
    }
    fun stroke(s: CompanionStroke) {
        val color = Color(android.graphics.Color.parseColor(s.color))
        liveReset?.cancel()
        live = when (s.kind) {
            "begin" -> LiveStroke(s.page, s.eraser, color, s.size, s.opacity, s.points)
            "move", "end" -> live?.takeIf { it.page == s.page }?.let { it.copy(points = (it.points + s.points).takeLast(8000)) }
            else -> null
        }
        // A lost phone must not leave its stroke over the page.
        if (live != null) liveReset = scope.launch { delay(4000); live = null }
    }
    /** The stretches that have a drawing; one the phone is still editing stays plain text until saved. */
    suspend fun textPieces(section: Int, text: String): List<TextPiece> = withContext(io) {
        val rows = runCatching { store.excerpts(comicId, section) }.getOrDefault(emptyList())
            .filter { store.file(comicId, it.page) != null && it.end <= text.length }
        val pieces = mutableListOf<TextPiece>()
        var at = 0
        for (row in rows) {
            if (row.start < at) continue
            val picture = runCatching { excerptPicture(row.page) }.getOrNull() ?: continue
            if (row.start > at) pieces += TextPiece.Words(text.substring(at, row.start).trimEnd('\n'))
            pieces += TextPiece.Picture(picture)
            at = row.end
        }
        if (at < text.length) pieces += TextPiece.Words(text.substring(at).trimStart('\n'))
        pieces.ifEmpty { listOf(TextPiece.Words(text)) }
    }
    private fun excerptPicture(page: Int): Bitmap? {
        val base = store.excerptBase(comicId, page) ?: return null
        val image = BitmapFactory.decodeFile(base.path, BitmapFactory.Options().apply { inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: return null
        val drawing = store.file(comicId, page)?.let { BitmapFactory.decodeFile(it.path) } ?: return image
        AndroidCanvas(image).drawBitmap(drawing, null, RectF(0f, 0f, image.width.toFloat(), image.height.toFloat()), AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG))
        drawing.recycle()
        return image
    }
    /** Replaces one rectangle of the layer, in the phone's layer coordinates. Transparent pixels erase. */
    suspend fun patch(page: Int, patch: Bitmap, x: Int, y: Int, width: Int, height: Int, layerWidth: Int, layerHeight: Int) {
        val known = withContext(Dispatchers.Main.immediate) { layers.containsKey(page) }
        val loaded = if (known) null else withContext(io) { runCatching { decode(page, 0, 0) }.getOrNull() }
        withContext(Dispatchers.Main.immediate) {
            if (!layers.containsKey(page) && loaded != null) layers[page] = loaded
            val scale = minOf(1f, LAYER_LONG_SIDE.toFloat() / max(layerWidth, layerHeight))
            val layer = layers[page]?.takeIf { abs(it.width.toFloat() / it.height - layerWidth.toFloat() / layerHeight) < .02f }
                ?: Bitmap.createBitmap((layerWidth * scale).roundToInt().coerceAtLeast(1), (layerHeight * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val sx = layer.width.toFloat() / layerWidth; val sy = layer.height.toFloat() / layerHeight
            AndroidCanvas(layer).drawBitmap(patch, null, RectF(x * sx, y * sy, (x + width) * sx, (y + height) * sy),
                AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) })
            patch.recycle()
            layers[page] = layer
            if (live?.page == page) { liveReset?.cancel(); live = null }
            version++
        }
    }
}

internal sealed interface TextPiece {
    data class Words(val text: String) : TextPiece
    data class Picture(val bitmap: Bitmap) : TextPiece
}

/** A section's text with its drawn stretches in place. Hidden drawings bring the plain text back. */
@Composable internal fun rememberTextPieces(drawings: PageDrawings, section: Int, text: String): List<TextPiece> {
    val hidden = drawings.hidden
    val pieces by produceState(listOf<TextPiece>(TextPiece.Words(text)), section, text, hidden, drawings.version) {
        value = if (hidden) listOf(TextPiece.Words(text)) else drawings.textPieces(section, text)
    }
    return pieces
}

/** Drawn over the page tiles with the same placement, so zoom, pan and turns carry the drawing along. */
@Composable internal fun PageDrawingOverlay(drawings: PageDrawings, pages: List<PageImage?>, indices: List<Int>, placements: List<ZoomPlacement>) {
    if (drawings.hidden) return
    val version = drawings.version
    LaunchedEffect(version, indices, pages.map { it?.file }) {
        pages.forEachIndexed { i, page -> if (page != null && i < indices.size) drawings.request(indices[i], page.width, page.height) }
    }
    val live = drawings.live
    val shown = indices.indices.filter { i -> i < pages.size && pages[i] != null && i < placements.size && (drawings.peek(indices[i]) != null || live?.page == indices[i]) }
    if (shown.isEmpty()) return
    // Its own layer: the eraser preview clears this drawing, never the page under it.
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        for (i in shown) {
            val place = placements[i]
            clipRect(place.left, place.top, place.left + place.width, place.top + place.height) {
                drawings.peek(indices[i])?.let { layer ->
                    drawImage(layer.asImageBitmap(), dstOffset = IntOffset(place.left.roundToInt(), place.top.roundToInt()),
                        dstSize = IntSize(place.width.roundToInt().coerceAtLeast(1), place.height.roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.High)
                }
                live?.takeIf { it.page == indices[i] && it.points.size >= 2 }?.let { stroke ->
                    fun at(n: Int) = Offset(place.left + stroke.points[n] * place.width, place.top + stroke.points[n + 1] * place.height)
                    val width = (stroke.size * place.width).coerceAtLeast(1f)
                    val blend = if (stroke.eraser) BlendMode.Clear else BlendMode.SrcOver
                    val alpha = if (stroke.eraser) 1f else stroke.opacity
                    if (stroke.points.size == 2) drawCircle(stroke.color, width / 2, at(0), alpha, blendMode = blend)
                    else {
                        val path = Path().apply {
                            at(0).let { moveTo(it.x, it.y) }
                            for (n in 2 until stroke.points.size - 1 step 2) at(n).let { lineTo(it.x, it.y) }
                        }
                        drawPath(path, stroke.color, alpha, Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
                    }
                }
            }
        }
    }
}
