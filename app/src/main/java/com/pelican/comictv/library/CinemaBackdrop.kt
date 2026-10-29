package com.pelican.comictv.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.pelican.comictv.cinema.CinemaImagePolicy
import com.pelican.comictv.ui.LocalThemeSpec
import com.pelican.comictv.ui.TvColors
import kotlinx.coroutines.delay

/** Manual horizontal art wins; the actual edition cover supplies an automatic local fallback. */
@Composable internal fun CinemaBackdrop(id: String?, repository: LibraryRepository, modifier: Modifier = Modifier,
    onImageReady: () -> Unit = {}) {
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
            // Only the old and new bounded decode coexist during this short opacity transition.
            // Compose's animation duration scale also honors Android's reduced/disabled animations.
            Crossfade(artwork, modifier = Modifier.fillMaxSize(), animationSpec = tween(theme.motion.artworkCrossfadeMs), label = "hero-artwork") { image ->
                val size = image?.let { CinemaImagePolicy.sourceDecodeSize(with(density) { maxWidth.roundToPx() },
                    with(density) { maxHeight.roundToPx() }, it.width, it.height, manager?.isLowRamDevice == true) }
                val request = remember(context, image, revision, size) { image?.let {
                    val targetSize = requireNotNull(size)
                    ImageRequest.Builder(context).data(it.file).size(targetSize.first, targetSize.second)
                        .precision(coil3.size.Precision.EXACT).scale(coil3.size.Scale.FIT)
                        .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
                } }
                if (request != null) AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    onSuccess = { onImageReady() })
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
