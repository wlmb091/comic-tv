package com.pelican.comictv.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.request.ImageRequest
import com.pelican.comictv.cinema.CinemaImagePolicy
import com.pelican.comictv.ui.LocalThemeSpec
import com.pelican.comictv.ui.TvColors
import kotlinx.coroutines.delay

private data class HeroArtwork(val id: String, val image: LibraryBackdropImage, val index: Int)
private class CarouselPanAlignment(private val progress: State<Float>) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset =
        BiasAlignment(0f, HeroCarouselPolicy.verticalBias(progress.value)).align(size, space, layoutDirection)
}

/** Manual horizontal art wins; the actual edition cover supplies an automatic local fallback. */
@Composable internal fun CinemaBackdrop(id: String?, repository: LibraryRepository, modifier: Modifier = Modifier,
    onImageReady: () -> Unit = {}) {
    val revision by repository.cinemaRevision.collectAsState()
    val theme = LocalThemeSpec.current
    val enabled = theme.artwork && !TvColors.options.highContrast
    val carousel = LocalHeroCarouselInfo.current
    val progress = LocalHeroCarouselProgress.current
    var artwork by remember(repository, enabled) { mutableStateOf<HeroArtwork?>(null) }
    var previous by remember(repository, enabled) { mutableStateOf<HeroArtwork?>(null) }
    var readyId by remember(repository, enabled) { mutableStateOf<String?>(null) }
    val turn = remember { Animatable(1f) }
    val pageTurn = carousel.enabled && carousel.running && enabled
    LaunchedEffect(id, revision, enabled) {
        if (!enabled || id == null) { artwork = null; previous = null }
        if (enabled && id != null) {
            delay(theme.motion.artworkSettleMs)
            try {
                val next = repository.libraryBackdrop(id)?.let { HeroArtwork(id, it, carousel.index) }
                // Explicitly retain at most one outgoing artwork, even when selection changes rapidly.
                val outgoing = artwork.takeIf { pageTurn && next != null && it?.id != next.id }
                if (outgoing != null) turn.snapTo(0f)
                previous = outgoing
                artwork = next
            }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { artwork = null; previous = null }
        }
    }
    LaunchedEffect(artwork, readyId, pageTurn) {
        if (previous != null && pageTurn) {
            turn.snapTo(0f)
            if (readyId != artwork?.id) return@LaunchedEffect
            turn.animateTo(1f, tween(theme.motion.artworkCrossfadeMs, easing = CubicBezierEasing(.23f, 1f, .32f, 1f)))
        } else turn.snapTo(1f)
        previous = null
    }
    BoxWithConstraints(modifier.clipToBounds().background(if (TvColors.options.highContrast) Color.Black else TvColors.Background)) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val manager = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
        if (enabled) {
            val widthPx = with(density) { maxWidth.toPx() }
            @Composable fun ArtworkLayer(frame: HeroArtwork, outgoing: Boolean) {
                val image = frame.image
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
                // Read the frame clock from the image's draw alignment, outside composition/layout.
                val movement = if (carousel.enabled) HeroCarouselPolicy.motion(TvColors.options.carouselMotion, frame.index) else "None"
                val alignment = if (movement != "Pan" || !TvColors.options.carouselVerticalPan) Alignment.Center else if (frame.id == carousel.id && !outgoing)
                    remember(progress) { CarouselPanAlignment(progress) } else Alignment.TopCenter
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val zoom = HeroCarouselPolicy.zoom(movement, if (outgoing) 1f else progress.value)
                    scaleX = zoom
                    scaleY = zoom
                    translationX = if (previous == null) 0f else if (outgoing) -widthPx * .08f * turn.value
                        else widthPx * (1f - turn.value)
                }) {
                    AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        alignment = alignment, filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                        onSuccess = { if (!outgoing) { readyId = frame.id; onImageReady() } },
                        onError = { if (!outgoing && artwork?.id == frame.id) { previous = null; artwork = null } })
                    // A small painted edge suggests a turning sheet without bitmap copies or 3D meshes.
                    if (!outgoing && previous != null) Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(32.dp)
                        .graphicsLayer { alpha = (1f - turn.value) * .24f }
                        .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .16f), Color.Black, Color.Transparent))))
                }
            }
            listOfNotNull(previous, artwork).distinctBy { it.id }.forEach { frame ->
                key(frame.id) { ArtworkLayer(frame, outgoing = frame.id != artwork?.id) }
            }
            // Dim only the cover backdrop; foreground content and card covers remain unchanged.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .15f)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                Color.Black.copy(alpha = .80f), Color.Black.copy(alpha = .32f), Color.Black.copy(alpha = .04f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = .05f), Color.Transparent, TvColors.Background))))
        }
    }
}
