package com.pelican.comictv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** Registered before navigation/reader handlers: only a Back that would exit reaches this fallback. */
@Composable internal fun AppExitGuard(onExit: () -> Unit, content: @Composable () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !confirming) { confirming = true }
    content()
    if (confirming) {
        val continueFocus = remember { FocusRequester() }
        com.pelican.comictv.ui.ComicDialog(onDismissRequest = { confirming = false }) {
            com.pelican.comictv.companion.CompanionDialogWindow { confirming = false }
            Column(Modifier.fillMaxWidth().widthIn(max = 600.dp).heightIn(max = 520.dp)
                .background(TvColors.Background, RoundedCornerShape(8.dp))
                .padding(24.dp).verticalScroll(rememberScrollState()).testTag("exit-confirmation"),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Sair do Comic TV?", fontSize = 24.sp)
                Text("Deseja realmente fechar o aplicativo?", fontSize = 18.sp, color = TvColors.Muted)
                TvButton("Continuar no aplicativo", { confirming = false },
                    Modifier.focusRequester(continueFocus).testTag("exit-continue"))
                TvButton("Sair do aplicativo", { confirming = false; onExit() }, Modifier.testTag("exit-confirm"))
            }
        }
        LaunchedEffect(Unit) { continueFocus.requestFocus() }
    }
}
