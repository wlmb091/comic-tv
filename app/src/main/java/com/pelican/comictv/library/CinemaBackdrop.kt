package com.pelican.comictv.library

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
import coil3.request.crossfade
import com.pelican.comictv.cinema.CinemaImagePolicy
import com.pelican.comictv.ui.ColorPreset
import com.pelican.comictv.ui.TvColors
import kotlinx.coroutines.delay

/** Manual horizontal art wins; the actual edition cover supplies an automatic local fallback. */
@Composable internal fun CinemaBackdrop(id: String?, repository: LibraryRepository, modifier: Modifier = Modifier) {
    val revision by repository.cinemaRevision.collectAsState()
    val enabled = TvColors.options.preset in listOf(ColorPreset.Flix, ColorPreset.Prime, ColorPreset.Max, ColorPreset.Cinema) && !TvColors.options.highContrast
    var artwork by remember(id, revision, enabled) { mutableStateOf<LibraryBackdropImage?>(null) }
    LaunchedEffect(id, revision, enabled) {
        artwork = null
        if (enabled && id != null) {
            delay(180)
            try { artwork = repository.libraryBackdrop(id) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { artwork = null }
        }
    }
    BoxWithConstraints(modifier.background(if (TvColors.options.highContrast) Color.Black else TvColors.Background)) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val manager = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
        val size = artwork?.let { CinemaImagePolicy.sourceDecodeSize(with(density) { maxWidth.roundToPx() },
            with(density) { maxHeight.roundToPx() }, it.width, it.height, manager?.isLowRamDevice == true) }
        val request = remember(context, artwork, revision, size) { artwork?.let {
            val targetSize = requireNotNull(size)
            ImageRequest.Builder(context).data(it.file).size(targetSize.first, targetSize.second)
                .crossfade(160)
                .precision(coil3.size.Precision.EXACT)
                // Decode the bounded source aspect ratio; only drawing crops to the viewport.
                .scale(coil3.size.Scale.FIT)
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
        } }
        if (enabled && request != null) {
            AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                Color.Black.copy(alpha = .80f), Color.Black.copy(alpha = .32f), Color.Black.copy(alpha = .04f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = .05f), Color.Transparent, TvColors.Background))))
        }
    }
}
