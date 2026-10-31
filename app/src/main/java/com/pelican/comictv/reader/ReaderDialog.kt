package com.pelican.comictv.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.pelican.comictv.library.LibraryDialog
import com.pelican.comictv.ui.TvColors

/** Dialog windows do not inherit the parent's graphics layer. Rotate their own viewport. */
@Composable internal fun ReaderDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!LocalReaderRotated.current) {
        LibraryDialog(title, onDismiss, content)
    } else {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            com.pelican.comictv.companion.CompanionDialogWindow(onDismiss)
            ReaderSurface(true) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = 520.dp)
                        .background(TvColors.Background).padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(title, fontSize = 24.sp)
                        content()
                    }
                }
            }
        }
    }
}
