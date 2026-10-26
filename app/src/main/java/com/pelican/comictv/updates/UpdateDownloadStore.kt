package com.pelican.comictv.updates

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Only this updater's private directory is touched. Offered APKs remain readable by Android. */
internal class UpdateDownloadStore(directory: File, private val freeBytes: () -> Long,
    private val verifyApk: suspend (File, UpdateManifest) -> Unit,
    private val fetch: suspend (UpdateManifest, File, (Long) -> Unit) -> Unit) {
    private val root = directory.canonicalFile
    private val lock = Mutex()
    private fun file(update: UpdateManifest) = File(root, "Comic-TV-${update.versionName}-${update.versionCode}.apk")
    private fun owned(value: File): File {
        val absolute = value.absoluteFile
        require(absolute.parentFile == root && absolute.canonicalFile == absolute && absolute.name.matches(Regex("Comic-TV-[0-9]+\\.[0-9]+\\.[0-9]+-[0-9]+\\.apk")))
        return absolute
    }
    private fun marker(value: File) = File(root, value.name + ".offered")
    suspend fun download(update: UpdateManifest, progress: (Long) -> Unit): File = lock.withLock {
        check(root.isDirectory || root.mkdirs()) { "Não foi possível preparar a atualização." }
        val target = file(update)
        if (target.exists()) {
            try { validate(target, update); progress(update.sizeBytes); return@withLock target }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (marker(target).exists()) throw IOException("O APK oferecido ao instalador mudou. Feche o instalador antes de tentar novamente.", e)
                check(owned(target).delete()) { "Não foi possível substituir a atualização inválida." }
            }
        }
        root.listFiles().orEmpty().filter { it != target && it.name.endsWith(".apk") && !marker(it).exists() }.forEach { old ->
            runCatching { owned(old) }.getOrNull()?.takeIf { it.isFile }?.delete()
        }
        require(freeBytes() >= update.sizeBytes + 64L * 1024 * 1024) { "Espaço insuficiente para atualizar. Libere espaço em Armazenamento." }
        val part = File.createTempFile("update-", ".part", root)
        try {
            var previous = 0L
            fetch(update, part) { received ->
                require(received in previous..update.sizeBytes) { "Tamanho da atualização inválido." }
                previous = received; progress(received)
            }
            currentCoroutineContext().ensureActive()
            validateBytes(part, update)
            verifyApk(part, update)
            currentCoroutineContext().ensureActive()
            require(!target.exists() && part.renameTo(target)) { "Não foi possível guardar a atualização." }
            target
        } finally { part.delete() }
    }
    suspend fun revalidate(value: File, update: UpdateManifest) = lock.withLock { validate(value, update) }
    private suspend fun validate(value: File, update: UpdateManifest) {
        require(owned(value) == file(update)) { "Arquivo de atualização inesperado." }
        validateBytes(value, update); verifyApk(value, update)
    }
    private suspend fun validateBytes(value: File, update: UpdateManifest) {
        require(value.isFile && value.length() == update.sizeBytes) { "O tamanho do APK não corresponde à versão publicada." }
        val digest = MessageDigest.getInstance("SHA-256")
        value.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == update.sha256) { "A integridade do APK não corresponde à versão publicada." }
    }
    suspend fun markOffered(value: File) = lock.withLock {
        require(owned(value).isFile)
        require(marker(value).canonicalFile == marker(value).absoluteFile)
        marker(value).writeText("offered")
    }
    suspend fun installationReturned(value: File) = lock.withLock { marker(owned(value)).delete(); Unit }
    suspend fun cleanupInstalled(installed: Long) = lock.withLock {
        if (!root.isDirectory) return@withLock
        root.listFiles().orEmpty().forEach { value ->
            val match = Regex("Comic-TV-[0-9]+\\.[0-9]+\\.[0-9]+-([0-9]+)\\.apk").matchEntire(value.name)
            if (match != null && (match.groupValues[1].toLongOrNull() ?: Long.MAX_VALUE) <= installed) {
                val safe = runCatching { owned(value) }.getOrNull()
                if (safe != null) { safe.delete(); marker(safe).delete() }
            } else if (value.name.startsWith("update-") && value.name.endsWith(".part") && value.canonicalFile == value.absoluteFile) value.delete()
        }
    }
}
