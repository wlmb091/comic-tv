package com.pelican.comictv.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.pelican.comictv.cinema.CinemaImagePolicy
import com.pelican.comictv.ui.LocalThemeSpec
import com.pelican.comictv.ui.TvColors
import kotlinx.coroutines.delay

/** Manual horizontal art wins; the actual edition cover supplies an automatic local fallback. */
@Composable internal fun CinemaBackdrop(id: String?, repository: LibraryRepository, modifier: Modifier = Modifier,
    onImageReady: () -> Unit = {}, dimmed: Boolean = false) {
    val revision by repository.cinemaRevision.collectAsState()
    val theme = LocalThemeSpec.current
    val enabled = theme.artwork && !TvColors.options.highContrast
    var artwork by remember(repository, enabled) { mutableStateOf<LibraryBackdropImage?>(null) }
    LaunchedEffect(id, revision, enabled) {
        if (!enabled || id == null) artwork = null
        if (enabled && id != null) {
            delay(theme.motion.artworkSettleMs)
            try { artwork = repository.libraryBackdrop(id) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { artwork = null }
        }
    }
    BoxWithConstraints(modifier.background(if (TvColors.options.highContrast) Color.Black else TvColors.Background)) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val manager = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
        if (enabled) {
            // The old image stays until the new decode finishes, so a change never shows an empty frame.
            // Only the old and new bounded decode coexist during this short opacity transition.
            var settled by remember(repository) { mutableStateOf<LibraryBackdropImage?>(null) }
            LaunchedEffect(artwork == null) { if (artwork == null) settled = null }
            // Each new image takes the other effect: zoom, then scroll, then zoom again.
            val cycle = remember(repository) { intArrayOf(0) }
            val moving = TvColors.options.carouselMotionOn && !dimmed && TvColors.options.preset in setOf(com.pelican.comictv.ui.ColorPreset.Flix,
                com.pelican.comictv.ui.ColorPreset.Max, com.pelican.comictv.ui.ColorPreset.Cinema)
            listOfNotNull(settled, artwork?.takeIf { it != settled }).forEach { image ->
                key(image) {
                    val effect = remember { CarouselMotion.effect(cycle[0]++, image.width, image.height, maxWidth.value, maxHeight.value) }
                    // Only the draw phase reads this clock, so a frame never recomposes the screen. Without frames the image stays still.
                    val clock = remember { mutableLongStateOf(0L) }
                    if (moving) LaunchedEffect(Unit) {
                        val start = withFrameNanos { it }
                        while (true) withFrameNanos { clock.longValue = (it - start) / 1_000_000 }
                    }
                    var ready by remember { mutableStateOf(image == settled) }
                    val layerAlpha by animateFloatAsState(if (ready) 1f else 0f, tween(theme.motion.artworkCrossfadeMs), label = "hero-artwork",
                        finishedListener = { if (ready && image == artwork) settled = image })
                    val size = CinemaImagePolicy.sourceDecodeSize(with(density) { maxWidth.roundToPx() },
                        with(density) { maxHeight.roundToPx() }, image.width, image.height, manager?.isLowRamDevice == true)
                    val filterOptions = TvColors.options.carouselImageOptions
                    val enhancement = TvColors.options.preset in setOf(com.pelican.comictv.ui.ColorPreset.Flix,
                        com.pelican.comictv.ui.ColorPreset.Max, com.pelican.comictv.ui.ColorPreset.Cinema) &&
                        filterOptions.preset != com.pelican.comictv.reader.ImagePreset.Original
                    val cappedSize = if (enhancement && size.first.toLong() * size.second > 2_000_000L) {
                        val ratio = kotlin.math.sqrt(2_000_000.0 / (size.first.toDouble() * size.second))
                        Pair((size.first * ratio).toInt().coerceAtLeast(1), (size.second * ratio).toInt().coerceAtLeast(1))
                    } else size
                    val request = remember(context, image, revision, cappedSize, enhancement, filterOptions) {
                        ImageRequest.Builder(context).data(image.file).size(cappedSize.first, cappedSize.second)
                            .precision(coil3.size.Precision.EXACT).scale(coil3.size.Scale.FIT)
                            .apply { if (enhancement) { allowHardware(false); transformations(CarouselCoverEnhancement(filterOptions)) } }
                            .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
                    }
                    val scroll = moving && effect == CarouselMotion.Effect.Scroll
                    val imageHeight = if (scroll) maxWidth * (image.height.toFloat() / image.width) else maxHeight
                    val overflow = (imageHeight - maxHeight).coerceAtLeast(androidx.compose.ui.unit.Dp(0f))
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        AsyncImage(request, null, (if (scroll) Modifier.fillMaxWidth().wrapContentHeight(androidx.compose.ui.Alignment.Top, unbounded = true).height(imageHeight)
                            else Modifier.fillMaxSize()).graphicsLayer {
                                alpha = layerAlpha
                                if (moving) {
                                    val t = clock.longValue
                                    if (scroll) translationY = -overflow.toPx() * CarouselMotion.scroll(t)
                                    else { val s = CarouselMotion.zoom(t); scaleX = s; scaleY = s }
                                }
                            }, contentScale = if (scroll) ContentScale.FillWidth else ContentScale.Crop,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                            onSuccess = { ready = true; onImageReady() })
                    }
                }
            }
            // Dim only the cover backdrop; foreground content and card covers remain unchanged.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                Color.Black.copy(alpha = .86f), Color.Black.copy(alpha = .5f), Color.Black.copy(alpha = .12f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = .05f), Color.Transparent, TvColors.Background))))
            // Rows sit directly over this art once the hero has scrolled away.
            val rowsShade by animateFloatAsState(if (dimmed) .74f else 0f, tween(theme.motion.artworkCrossfadeMs), label = "backdrop-rows-shade")
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = rowsShade }.background(Color.Black))
        }
    }
}

/** The carousel's optional motion: pure functions of the time since the image appeared, so they never decide the swap. */
internal object CarouselMotion {
    enum class Effect { Zoom, Scroll }
    /** Even turns zoom; odd turns scroll, when the art is tall enough to scroll at least 8% of the screen. */
    fun effect(turn: Int, width: Int, height: Int, viewWidth: Float, viewHeight: Float): Effect {
        val tall = width > 0 && viewWidth > 0 && viewHeight > 0 && height.toFloat() / width * viewWidth >= viewHeight * 1.08f
        return if (turn % 2 == 1 && tall) Effect.Scroll else Effect.Zoom
    }
    /** 108% at the start, 100% halfway, 108% again at 8 seconds. */
    fun zoom(ms: Long): Float = 1f + .04f * (1f + kotlin.math.cos(2.0 * Math.PI * (ms % PERIOD_MS) / PERIOD_MS).toFloat())
    /** 0 at the top to 1 at the bottom in 7 seconds, a second still, then back up if the image stays longer. */
    fun scroll(ms: Long): Float {
        val t = ms % (2 * PERIOD_MS)
        val down = (t.coerceAtMost(PERIOD_MS) / 7000f).coerceAtMost(1f)
        val p = if (t < PERIOD_MS) down else 1f - ((t - PERIOD_MS) / 7000f).coerceAtMost(1f)
        return p * p * (3f - 2f * p)
    }
    const val PERIOD_MS = 8_000L
}
