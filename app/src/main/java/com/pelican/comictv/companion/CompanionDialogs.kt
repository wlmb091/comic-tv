package com.pelican.comictv.companion

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Only windows explicitly registered by this app's composition can receive LAN input. Main thread only. */
internal class CompanionDialogs {
    data class Entry(val window:Window,val dismiss:()->Unit)
    private val entries=mutableListOf<Entry>()
    fun register(window:Window,dismiss:()->Unit):()->Unit {
        val entry=Entry(window,dismiss);entries+=entry
        return {entries.remove(entry);Unit}
    }
    fun focused():Entry?=entries.lastOrNull{it.window.decorView.hasWindowFocus()}
    fun dismissAll(){entries.toList().asReversed().forEach{it.dismiss()}}
}
internal val LocalCompanionDialogs=compositionLocalOf<CompanionDialogs?>{null}

/** Call first inside a Dialog's content: while it holds TV focus, the phone controls it instead of being refused. */
@Composable internal fun CompanionDialogWindow(onDismiss:()->Unit) {
    val bridge=LocalCompanionDialogs.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    val window=(LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(bridge,window){
        val unregister=window?.let{bridge?.register(it){latestDismiss()}}
        onDispose{unregister?.invoke()}
    }
}
