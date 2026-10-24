package com.pelican.comictv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

/** Persistent selection is outlined; the remote's current focus is filled. */
@Composable internal fun TvChoice(
    text: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 36.dp else 48.dp)
            .semantics { this.selected = selected },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 4.dp else 10.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = TvColors.Panel,
            contentColor = if (selected) TvColors.Amber else TvColors.Muted,
            focusedContainerColor = TvColors.Action,
            focusedContentColor = TvColors.OnAction,
        ),
        border = ButtonDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, TvColors.Amber)) else Border.None,
            focusedBorder = Border(BorderStroke(if (TvColors.options.highContrast) 3.dp else 2.dp, TvColors.Amber)),
        ),
    ) { Text(text, fontSize = if (compact) 14.sp else 17.sp) }
}
