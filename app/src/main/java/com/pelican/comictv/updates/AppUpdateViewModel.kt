package com.pelican.comictv.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal interface AppUpdateSource {
    suspend fun check(): UpdateManifest?
    suspend fun download(update: UpdateManifest, progress: (Long) -> Unit): File
    suspend fun revalidate(file: File, update: UpdateManifest)
    suspend fun markOffered(file: File)
    suspend fun installationReturned(file: File)
    suspend fun installedVersionCode(): Long
}
internal enum class AppUpdatePhase { Idle, Checking, Current, Available, Downloading, Ready, Verifying, Installing, Failed }
internal data class AppUpdateState(val phase: AppUpdatePhase = AppUpdatePhase.Idle, val update: UpdateManifest? = null,
    val file: File? = null, val received: Long = 0, val message: String? = null)

internal class AppUpdateViewModel(private val source: AppUpdateSource) : ViewModel() {
    private val _state = MutableStateFlow(AppUpdateState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val generation = AtomicLong()
    private var automaticAttempted = false
    private fun busy() = _state.value.phase in setOf(AppUpdatePhase.Checking, AppUpdatePhase.Downloading, AppUpdatePhase.Verifying, AppUpdatePhase.Installing)
    fun automaticCheck() { if (!automaticAttempted) { automaticAttempted = true; check() } }
    fun check() {
        if (busy()) return
        operate(AppUpdatePhase.Checking) {
            val update = source.check()
            currentCoroutineContext().ensureActive()
            _state.value = if (update == null) AppUpdateState(AppUpdatePhase.Current, message = "Você está usando a versão mais recente publicada.")
                else AppUpdateState(AppUpdatePhase.Available, update)
        }
    }
    fun download() {
        if (busy()) return
        val update = _state.value.update ?: return
        val token = generation.get() + 1
        operate(AppUpdatePhase.Downloading) {
            val result = source.download(update) { received ->
                if (generation.get() == token) _state.update { old ->
                    if (old.phase == AppUpdatePhase.Downloading) old.copy(received = received.coerceIn(0, update.sizeBytes)) else old
                }
            }
            currentCoroutineContext().ensureActive()
            _state.value = AppUpdateState(AppUpdatePhase.Ready, update, result, update.sizeBytes, "Download verificado. Escolha Instalar atualização.")
        }
    }
    fun install(launch: (File) -> Unit) {
        val current = _state.value
        if (current.phase != AppUpdatePhase.Ready) return
        val update = current.update ?: return; val file = current.file ?: return
        operate(AppUpdatePhase.Verifying) {
            source.revalidate(file, update); currentCoroutineContext().ensureActive()
            source.markOffered(file); currentCoroutineContext().ensureActive()
            _state.value = current.copy(phase = AppUpdatePhase.Installing, message = "Confirme a instalação na tela do Android.")
            launch(file)
        }
    }
    fun continueInstall(file: File, launch: (File) -> Unit) {
        val current = _state.value
        if (current.phase != AppUpdatePhase.Installing || current.file != file) return
        job = viewModelScope.launch {
            try { source.revalidate(file, requireNotNull(current.update)); ensureActive(); launch(file) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                ensureActive()
                // Permission screen returned, but the installer has not been launched yet.
                try { source.installationReturned(file) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                _state.value = current.copy(phase = AppUpdatePhase.Failed, message = "O APK mudou ou não pode mais ser instalado. Verifique e baixe novamente.")
            }
        }
    }
    fun installationReturned(message: String? = null) {
        val current = _state.value; val file = current.file ?: return
        job = viewModelScope.launch {
            try {
                source.installationReturned(file)
                val installed = source.installedVersionCode(); ensureActive()
                _state.value = if (current.update?.isNewerThan(installed) == false) AppUpdateState(AppUpdatePhase.Current, message = "Atualização instalada.")
                    else current.copy(phase = AppUpdatePhase.Ready, message = message ?: "Instalação não concluída. Você pode tentar novamente.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = current.copy(phase = AppUpdatePhase.Failed, message = "Não foi possível conferir a instalação. Verifique a versão do aplicativo.") }
        }
    }
    private fun operate(phase: AppUpdatePhase, action: suspend () -> Unit) {
        val token = generation.incrementAndGet()
        job?.cancel()
        _state.update { it.copy(phase = phase, received = 0, message = null) }
        job = viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (isActive && generation.get() == token) _state.update {
                it.copy(phase = AppUpdatePhase.Failed, message = e.message?.take(300) ?: "Não foi possível concluir a atualização. Tente novamente.")
            } }
        }
    }
    fun cancel() {
        if (_state.value.phase == AppUpdatePhase.Installing) return
        generation.incrementAndGet(); job?.cancel(); job = null
        _state.update { it.copy(phase = if (it.file != null) AppUpdatePhase.Ready else if (it.update != null) AppUpdatePhase.Available else AppUpdatePhase.Idle,
            received = 0, message = "Operação cancelada.") }
    }
    fun backgrounded() { if (_state.value.phase in setOf(AppUpdatePhase.Checking, AppUpdatePhase.Downloading, AppUpdatePhase.Verifying)) cancel() }
}
