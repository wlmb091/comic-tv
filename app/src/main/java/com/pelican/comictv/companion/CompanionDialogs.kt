package com.pelican.comictv.companion

import android.view.Window
import androidx.compose.runtime.compositionLocalOf

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
