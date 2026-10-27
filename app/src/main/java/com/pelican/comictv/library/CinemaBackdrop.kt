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
import com.pelican.comictv.cinema.CinemaImagePolicy
import com.pelican.comictv.ui.TvColors
import kotlinx.coroutines.delay
import java.io.File

/** Independent horizontal artwork only: a portrait cover is never used as a backdrop. */
@Composable internal fun CinemaBackdrop(id: String?, repository: LibraryRepository, modifier: Modifier = Modifier) {
    val revision by repository.cinemaRevision.collectAsState()
    var artwork by remember(id, revision) { mutableStateOf<File?>(null) }
    val enabled = (TvColors.options.cinemaMode || TvColors.options.preset == com.pelican.comictv.ui.ColorPreset.Cinema) && !TvColors.options.highContrast
    LaunchedEffect(id, revision, enabled) {
        artwork = null
        if (enabled && id != null) {
            delay(180)
            try { artwork = repository.cinemaArtwork(id) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { artwork = null }
        }
    }
    BoxWithConstraints(modifier.background(TvColors.Background)) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val manager = remember(context) { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
        val size = CinemaImagePolicy.decodeSize(with(density) { maxWidth.roundToPx() },
            with(density) { maxHeight.roundToPx() }, manager?.isLowRamDevice == true)
        val request = remember(artwork, revision, size) { artwork?.let {
            ImageRequest.Builder(context).data(it).size(size.first, size.second)
                .precision(coil3.size.Precision.EXACT)
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
        } }
        if (enabled && request != null) {
            AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                Color.Black.copy(alpha = .86f), Color.Black.copy(alpha = .48f), Color.Black.copy(alpha = .06f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                Color.Black.copy(alpha = .05f), Color.Transparent, TvColors.Background))))
        }
    }
}
