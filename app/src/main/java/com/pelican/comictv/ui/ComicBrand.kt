package com.pelican.comictv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.pelican.comictv.R
import java.io.File

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
                Image(painterResource(R.drawable.brand_wordmark), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)))
            }
        }
    }
}
