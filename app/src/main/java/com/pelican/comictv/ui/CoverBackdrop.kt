package com.pelican.comictv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.pelican.comictv.image.blurBackdropPixels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable internal fun CoverBackdrop(cover: File, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, cover.path, cover.lastModified()) {
        try {
            value = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(cover.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
                val decoded = BitmapFactory.decodeFile(cover.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: return@withContext null
                val small = Bitmap.createScaledBitmap(decoded, 96, 54, true)
                val pixels = IntArray(96 * 54)
                small.getPixels(pixels, 0, 96, 0, 0, 96, 54)
                if (small !== decoded) decoded.recycle()
                small.recycle()
                Bitmap.createBitmap(blurBackdropPixels(pixels, 96, 54), 96, 54, Bitmap.Config.ARGB_8888)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { value = null }
    }
    Box(modifier) {
        bitmap?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .76f)))
    }
}
