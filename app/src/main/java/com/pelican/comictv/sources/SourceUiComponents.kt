package com.pelican.comictv.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.pelican.comictv.R
import com.pelican.comictv.ui.TvColors

@Composable internal fun SourceSectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, fontFamily = FontFamily(Font(R.font.montserrat_extra_bold, FontWeight.ExtraBold)),
        fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable internal fun SourceModeTab(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.heightIn(min = 42.dp).semantics { selected = active },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            containerColor = if (active) TvColors.Amber.copy(alpha = .13f) else TvColors.Panel,
            contentColor = if (active) TvColors.Amber else TvColors.Muted,
            focusedContainerColor = TvColors.Action, focusedContentColor = TvColors.OnAction),
        border = ButtonDefaults.border(focusedBorder = Border(BorderStroke(2.dp, TvColors.Amber)))) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text((if (active) "✓ " else "") + label, fontSize = 15.sp)
            Text(count.toString(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable internal fun SourceEmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().background(TvColors.Panel, RoundedCornerShape(12.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = TvColors.Muted, fontSize = 16.sp, lineHeight = 23.sp)
    }
}
