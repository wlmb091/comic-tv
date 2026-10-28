package com.pelican.comictv.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pelican.comictv.ui.ComicWordmark
import com.pelican.comictv.ui.TvChoice
import com.pelican.comictv.ui.TvColors
import com.pelican.comictv.ui.ColorPreset

@Composable internal fun LibraryHeaderActions(
    libraryCount: Int, historyCount: Int, history: Boolean, searchActive: Boolean,
    onSearch: () -> Unit, onLibrary: () -> Unit, onHistory: () -> Unit,
    libraryModifier: Modifier = Modifier, searchModifier: Modifier = Modifier,
) {
    TvChoice("Buscar", searchActive, onSearch, searchModifier, compact = true)
    TvChoice("Biblioteca · $libraryCount", !history, onLibrary, libraryModifier, compact = true)
    TvChoice("Histórico · $historyCount", history, onHistory, compact = true)
}

@Composable internal fun LibraryHeader(
    libraryCount: Int, historyCount: Int, history: Boolean, searchActive: Boolean,
    onSearch: () -> Unit, onLibrary: () -> Unit, onHistory: () -> Unit,
    libraryModifier: Modifier = Modifier, searchModifier: Modifier = Modifier,
) {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val actions: @Composable () -> Unit = {
        LibraryHeaderActions(libraryCount, historyCount, history, searchActive,
            onSearch, onLibrary, onHistory, libraryModifier, searchModifier)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val showBrand = TvColors.options.preset in listOf(ColorPreset.Amber, ColorPreset.Max,
            ColorPreset.Green, ColorPreset.Blue, ColorPreset.Violet)
        if (!showBrand) {
            FlowRow(Modifier.fillMaxWidth().testTag("library-header-actions"),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
        } else if (maxWidth < 760.dp * scale) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ComicWordmark(Modifier.fillMaxWidth().heightIn(min = 38.dp).testTag("library-brand"))
                FlowRow(Modifier.fillMaxWidth().testTag("library-header-actions"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ComicWordmark(Modifier.width(180.dp * scale).heightIn(min = 38.dp).testTag("library-brand"))
                FlowRow(Modifier.weight(1f).testTag("library-header-actions"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
            }
        }
    }
}
