package com.pelican.comictv.details

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.pelican.comictv.library.LibraryRepository
import java.io.File
import kotlinx.coroutines.CancellationException

/** Decode for the actual display area; retain the complete page aspect ratio. */
@Composable internal fun DetailCoverImage(file: File?, description: String, modifier: Modifier = Modifier) {
    var pixels by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    val request = remember(file, pixels, context) {
        ImageRequest.Builder(context).data(file).apply {
            if (pixels.width > 0 && pixels.height > 0) size(pixels.width, pixels.height)
        }.build()
    }
    AsyncImage(request, description, modifier.onSizeChanged { pixels = it },
        contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
}

@Composable internal fun rememberDetailsCover(repository: LibraryRepository, id: String): File? {
    var cover by remember(id) { mutableStateOf<File?>(repository.cover(id)) }
    LaunchedEffect(repository, id) {
        cover = try { repository.detailsCover(id) ?: repository.cover(id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { repository.cover(id) }
    }
    return cover
}
