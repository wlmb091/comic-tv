package com.pelican.comictv.sources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class OnlineEditionsState(val context: OnlineEditions? = null, val reversed: Boolean = false,
    val busy: Boolean = true, val message: String? = null)

class OnlineEditionsViewModel(private val id: String, private val saved: SavedStateHandle,
    private val load: suspend (String) -> OnlineEditions,
    private val openChapter: suspend (OnlineEditions, OnlineChapter) -> String,
    private val readOrder: suspend (OnlineEditions) -> Boolean = { false },
    private val writeOrder: suspend (OnlineEditions, Boolean) -> Unit = { _, _ -> },
) : ViewModel() {
    private val _state = MutableStateFlow(OnlineEditionsState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var leaving = false
    init { retry() }

    fun leave() { leaving = true; job?.cancel(); job = null }

    fun retry() {
        if (leaving) return
        job?.cancel()
        _state.update { it.copy(busy = true, message = null) }
        job = viewModelScope.launch {
            try {
                val context = load(id)
                val reversed = saved.get<Boolean>("reversed") ?: readOrder(context)
                ensureActive()
                _state.value = OnlineEditionsState(context, reversed, busy = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive && !leaving) _state.update { it.copy(busy = false, message = e.message ?: "Não foi possível consultar as edições.") } }
        }
    }

    fun toggleOrder() {
        val old = _state.value
        val context = old.context ?: return
        if (old.busy || leaving) return
        _state.update { it.copy(busy = true, message = null) }
        job = viewModelScope.launch {
            try {
                val reversed = !old.reversed
                writeOrder(context, reversed)
                ensureActive()
                saved["reversed"] = reversed
                _state.update { it.copy(reversed = reversed, busy = false) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (isActive && !leaving) _state.update { it.copy(busy = false, message = "Não foi possível salvar a ordem da sequência.") } }
        }
    }

    fun openNeighbor(direction: Int, onRead: (String) -> Unit) {
        val old = _state.value
        if (old.busy || leaving || direction !in listOf(-1, 1)) return
        val context = old.context ?: return
        val sequence = chapterSequence(context.chapters, context.current.key, old.reversed)
        val neighbor = if (direction < 0) sequence.previous else sequence.next
        if (neighbor == null) {
            _state.update { it.copy(message = if (sequence.index < 0) "A edição atual não aparece na lista da fonte. Escolha uma edição."
                else if (direction < 0) "Você está na primeira edição desta sequência." else "Você está na última edição desta sequência.") }
            return
        }
        open(neighbor, onRead)
    }
    fun open(chapter: OnlineChapter, onRead: (String) -> Unit) {
        val old = _state.value
        if (old.busy || leaving) return
        val context = old.context ?: return
        val selected = context.chapters.firstOrNull { it.key == chapter.key }
        if (selected == null) { _state.update { it.copy(message = "Esta edição não está mais na lista da fonte.") }; return }
        _state.update { it.copy(busy = true, message = null) }
        job = viewModelScope.launch {
            try {
                val destination = openChapter(context, selected)
                ensureActive()
                _state.update { it.copy(busy = false) }
                onRead(destination)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive && !leaving) _state.update { it.copy(busy = false, message = e.message ?: "Não foi possível abrir esta edição.") } }
        }
    }
}
