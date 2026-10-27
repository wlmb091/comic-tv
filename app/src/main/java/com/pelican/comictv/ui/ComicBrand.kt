package com.pelican.comictv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.pelican.comictv.image.blurBackdropPixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.pelican.comictv.R
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val identityBackdropLock = Mutex()
private var identityBackdropBitmap: Bitmap? = null // One static nodpi asset, 96x54; theme tint stays outside.

@Composable internal fun ComicSignature(modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.brand_wordmark), "Comic TV",
            Modifier.fillMaxWidth().aspectRatio(1024f / 572f), contentScale = ContentScale.FillWidth)
    }
}

@Composable internal fun BrandedBackground(cover: File? = null, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        if (!TvColors.options.highContrast) {
            if (TvColors.options.coverBackground && cover?.isFile == true) {
                CoverBackdrop(cover, Modifier.fillMaxSize())
            } else {
                IdentityBackdrop(Modifier.fillMaxSize())
            }
        }
    }
}

// Software blur keeps the static backdrop compatible with Android 8 TVs.
@Composable private fun IdentityBackdrop(modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val bitmap by produceState<Bitmap?>(null, resources) {
        value = identityBackdropLock.withLock { identityBackdropBitmap ?: withContext(Dispatchers.IO) {
            val decoded = BitmapFactory.decodeResource(resources, R.drawable.brand_wordmark,
                BitmapFactory.Options().apply { inSampleSize = 4 }) ?: return@withContext null
            val small = Bitmap.createScaledBitmap(decoded, 96, 54, true)
            val pixels = IntArray(96 * 54)
            small.getPixels(pixels, 0, 96, 0, 0, 96, 54)
            if (small !== decoded) decoded.recycle()
            small.recycle()
            Bitmap.createBitmap(blurBackdropPixels(pixels, 96, 54), 96, 54, Bitmap.Config.ARGB_8888)
        }.also { identityBackdropBitmap = it } }
    }
    Box(modifier.background(TvColors.Background)) {
        bitmap?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        Box(Modifier.fillMaxSize().background(TvColors.Background.copy(alpha = .98f)))
    }
}

@Composable internal fun ComicWordmark(modifier: Modifier = Modifier, centered: Boolean = false) {
    Box(modifier, contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White)) { append("COMIC ") }
            withStyle(SpanStyle(color = TvColors.Amber)) { append("TV") }
        }, fontFamily = FontFamily(Font(R.font.montserrat_extra_bold, FontWeight.ExtraBold)),
            fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp,
            letterSpacing = (-.6).sp, maxLines = 1)
    }
}
