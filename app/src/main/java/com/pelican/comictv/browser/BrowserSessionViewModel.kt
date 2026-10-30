package com.pelican.comictv.browser

import android.content.Context
import android.util.AtomicFile
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import java.io.File

internal class BrowserSessionViewModel(context: Context) : ViewModel() {
    private val file = AtomicFile(File(context.filesDir, "browser-session.json"))
    private val gson = Gson()
    val webStates = mutableMapOf<String, android.os.Bundle>()
    private fun restore() = runCatching {
        if (file.baseFile.length() > 1024L * 1024) return@runCatching BrowserSessionState()
        file.openRead().bufferedReader().use { gson.fromJson(it, BrowserSessionState::class.java) } ?: BrowserSessionState()
    }.getOrDefault(BrowserSessionState())
    val session = runCatching { BrowserSession(restore(), ::save) }.getOrElse { BrowserSession(BrowserSessionState(), ::save) }
    private fun save(state: BrowserSessionState) = synchronized(file) {
        var stream: java.io.FileOutputStream? = null
        try { stream = file.startWrite(); stream.write(gson.toJson(state).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (_: Exception) { stream?.let(file::failWrite) }
    }
}
