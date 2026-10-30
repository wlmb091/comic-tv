package com.pelican.comictv.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pelican.comictv.ui.TvColors

/** Browser chrome is an overlay: showing controls must never shrink or re-layout the site. */
@Composable internal fun BrowserViewport(controlsVisible: Boolean = true, controls: @Composable () -> Unit, page: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(TvColors.Background)) {
        Box(Modifier.fillMaxSize()) { page() }
        if (controlsVisible) Box(Modifier.fillMaxSize()) { controls() }
    }
}
