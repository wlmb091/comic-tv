package com.pelican.comictv.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StorageUiState(val report: StorageReport? = null, val busy: Boolean = false,
    val cleanup: CacheCleanup? = null, val error: String? = null)

class StorageViewModel(private val load: suspend () -> StorageReport,
    private val cleanup: suspend (CacheSelection) -> CacheCleanup) : ViewModel() {
    private val _state = MutableStateFlow(StorageUiState())
    val state = _state.asStateFlow()
    init { refresh() }
    fun refresh() = operate(null)
    fun clearCaches(selection: CacheSelection) = operate(selection)
    private fun operate(selection: CacheSelection?) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, error = null, cleanup = null)
        viewModelScope.launch {
            try {
                if (selection != null) _state.value = _state.value.copy(cleanup = cleanup(selection))
                _state.value = _state.value.copy(report = load())
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = _state.value.copy(error = if (selection == null)
                "Não foi possível calcular o armazenamento. Tente atualizar."
                else "Não foi possível concluir a limpeza ou atualizar os valores. Tente novamente.") }
            finally { _state.value = _state.value.copy(busy = false) }
        }
    }
}
