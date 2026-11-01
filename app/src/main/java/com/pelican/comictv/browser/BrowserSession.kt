package com.pelican.comictv.browser

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class BrowserTab(val id: String = UUID.randomUUID().toString(), val title: String = "Nova aba", val url: String = "")
internal data class BrowserPage(val url: String, val title: String)
internal data class BrowserSessionState(val tabs: List<BrowserTab> = listOf(BrowserTab()), val selectedId: String = "",
    val bookmarks: List<BrowserPage> = emptyList(), val history: List<BrowserPage> = emptyList(),
    val desktop: Boolean = false, val cursor: Boolean = true, val cursorSpeed: Int = 1,
    val textZoom: Int = 100, val blockPopups: Boolean = true, val cursorSize: Int = 100, val cursorStyle: Int = 0)

/** Pointer looks the TV menu and the phone panel can pick: ring with dot (original), dot and mouse arrow. */
internal val cursorSizes = listOf(50, 75, 100, 150, 200, 250, 300)
internal val cursorStyleNames = listOf("Círculo com ponto", "Ponto", "Seta de mouse")
internal fun nextCursorSize(size: Int) = cursorSizes.firstOrNull { it > size } ?: cursorSizes.first()
internal fun nextCursorStyle(style: Int) = (style + 1) % cursorStyleNames.size
internal class BrowserSession(initial: BrowserSessionState = BrowserSessionState(), private val persist: (BrowserSessionState) -> Unit = {}) {
    private fun valid(url: String) = url.isNotBlank() && url.length <= 4096 && runCatching { BrowserPolicy.url(url) }.isSuccess
    private fun name(title: String) = title.filter { it.code >= 32 }.take(100).ifBlank { "Página" }
    private val restoredTabs = initial.tabs.take(8).filter { it.id.length in 1..80 }.distinctBy { it.id }.map {
        if (valid(it.url)) it.copy(title = name(it.title)) else it.copy(title = "Nova aba", url = "")
    }.ifEmpty { listOf(BrowserTab()) }
    private val data = MutableStateFlow(initial.copy(tabs = restoredTabs,
        selectedId = initial.selectedId.takeIf { id -> restoredTabs.any { it.id == id } } ?: restoredTabs.first().id,
        bookmarks = initial.bookmarks.filter { valid(it.url) }.distinctBy { it.url }.take(100),
        history = initial.history.filter { valid(it.url) }.distinctBy { it.url }.take(100), textZoom = initial.textZoom.coerceIn(75, 150), cursorSpeed = initial.cursorSpeed.coerceIn(0, 2),
        cursorSize = initial.cursorSize.coerceIn(50, 300), cursorStyle = initial.cursorStyle.coerceIn(0, cursorStyleNames.lastIndex)))
    val state = data.asStateFlow()
    private fun change(block: (BrowserSessionState) -> BrowserSessionState) { data.update(block); persist(data.value) }
    fun newTab(url: String = ""): String? {
        if (data.value.tabs.size >= 8 || url.isNotBlank() && !valid(url)) return null
        val tab = BrowserTab(url = url)
        change { it.copy(tabs = it.tabs + tab, selectedId = tab.id) }; return tab.id
    }
    fun close(id: String) = change { old ->
        val index = old.tabs.indexOfFirst { it.id == id }; if (index < 0) old else {
            val remaining = old.tabs.filterNot { it.id == id }.ifEmpty { listOf(BrowserTab()) }
            old.copy(tabs = remaining, selectedId = if (old.selectedId == id) remaining[(index - 1).coerceIn(0, remaining.lastIndex)].id else old.selectedId)
        }
    }
    fun select(id: String) { if (data.value.tabs.any { it.id == id }) change { it.copy(selectedId = id) } }
    fun home(id: String) = change { old -> old.copy(tabs = old.tabs.map { if (it.id == id) it.copy(url = "", title = "Nova aba") else it }) }
    fun page(id: String, url: String, title: String, finished: Boolean = false) {
        if (!valid(url) || data.value.tabs.none { it.id == id }) return
        val page = BrowserPage(url, name(title))
        change { old -> old.copy(tabs = old.tabs.map { if (it.id == id) it.copy(url = url, title = page.title) else it },
            history = if (finished) (listOf(page) + old.history.filterNot { it.url == url }).take(100) else old.history) }
    }
    fun bookmark(url: String, title: String) { if (valid(url)) change { old -> old.copy(bookmarks =
        if (old.bookmarks.any { it.url == url }) old.bookmarks.filterNot { it.url == url }
        else (old.bookmarks + BrowserPage(url, name(title))).takeLast(100)) } }
    fun historyClear() = change { it.copy(history = emptyList()) }
    fun options(desktop: Boolean = data.value.desktop, cursor: Boolean = data.value.cursor, speed: Int = data.value.cursorSpeed,
        textZoom: Int = data.value.textZoom, blockPopups: Boolean = data.value.blockPopups,
        cursorSize: Int = data.value.cursorSize, cursorStyle: Int = data.value.cursorStyle) = change {
        it.copy(desktop = desktop, cursor = cursor, cursorSpeed = speed.coerceIn(0, 2), textZoom = textZoom.coerceIn(75, 150), blockPopups = blockPopups,
            cursorSize = cursorSize.coerceIn(50, 300), cursorStyle = cursorStyle.coerceIn(0, cursorStyleNames.lastIndex))
    }
}
