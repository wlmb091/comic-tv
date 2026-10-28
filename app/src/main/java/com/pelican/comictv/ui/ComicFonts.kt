package com.pelican.comictv.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.pelican.comictv.R

/** OFL fonts are bundled; reading never waits for a font download. */
@OptIn(ExperimentalTextApi::class)
internal object ComicFonts {
    val Body = FontFamily(listOf(400, 500, 600, 700, 800).map { weight ->
        Font(R.font.inter_variable, FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    })
    val Cinema = FontFamily(Font(R.font.cinzel_variable, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))))
}
