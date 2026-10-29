package com.pelican.comictv.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pelican.comictv.ui.TvColors

/** Native controls can grow with downloads/text size without squeezing the site to zero height.
 * The WebView owns site scrolling; never nest it inside the controls' vertical scroller. */
@Composable internal fun BrowserViewport(controls: @Composable () -> Unit, page: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(TvColors.Background).padding(16.dp)) {
        val controlsLimit = maxHeight * .45f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = controlsLimit).verticalScroll(rememberScrollState())) {
                controls()
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { page() }
        }
    }
}
