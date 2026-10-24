package com.pelican.comictv.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DownloadsViewModel(private val repository: DownloadRepository) : ViewModel() {
    val state = repository.state
    private var worker: Job? = null
    private var entryJob: Job? = null
    private var requested = false
    private var closing = false
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error = _error.asStateFlow()
    fun enterScreen(id: String?) {
        val initial = if (!requested) id else null
        requested = true
        enter(initial)
    }
    fun enter(id: String?) {
        closing = false
        if (_busy.value) return
        _busy.value = true
        entryJob = viewModelScope.launch { try {
            repository.refresh()
            ensureActive()
            if (id != null) { repository.enqueue(id); ensureActive(); start() }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { _error.value = "Não foi possível abrir a fila. Confira o armazenamento." }
        finally { _busy.value = false } }
    }
    fun start() { if (closing || worker?.isCompleted == false) return; _error.value = null; worker = viewModelScope.launch { repository.runQueue() } }
    fun pause() { entryJob?.cancel(); worker?.cancel() }
    fun leave(action: () -> Unit) {
        if (closing) return
        closing = true; entryJob?.cancel(); worker?.cancel()
        viewModelScope.launch { entryJob?.join(); worker?.join(); action() }
    }
    fun remove(id: String) {
        if (_busy.value) return; _busy.value = true
        viewModelScope.launch { try { worker?.cancelAndJoin(); repository.remove(id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { _error.value = "Não foi possível remover todos os arquivos. Tente novamente." }
        finally { _busy.value = false } }
    }
    override fun onCleared() { entryJob?.cancel(); worker?.cancel() }
}
