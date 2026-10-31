package com.pelican.comictv.companion

import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import android.view.KeyEvent
import com.pelican.comictv.library.LibraryDialog
import com.pelican.comictv.ui.ComicTvTheme
import com.pelican.comictv.ui.TvButton
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompanionDialogTest {
    @get:Rule val compose=createComposeRule()
    @Test fun onlyRegisteredFocusedDialogReceivesInputAndUnregistersOnDismiss(){
        val registry=CompanionDialogs();var clicked=false
        compose.setContent { ComicTvTheme {
            var visible by remember { mutableStateOf(true) }
            CompositionLocalProvider(LocalCompanionDialogs provides registry){
                if(visible)LibraryDialog("Opções do link",{visible=false}){
                    TvButton("Cancelar",{clicked=true;visible=false})
                }
            }
        } }
        compose.onNodeWithText("Cancelar").performSemanticsAction(SemanticsActions.RequestFocus){it()}
        compose.runOnIdle {
            val entry=requireNotNull(registry.focused())
            entry.window.callback?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER))
            entry.window.callback?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER))
        }
        compose.waitForIdle();compose.runOnIdle{assertTrue(clicked);assertNull(registry.focused())}
    }
}
