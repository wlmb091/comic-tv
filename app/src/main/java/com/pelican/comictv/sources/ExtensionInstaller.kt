package com.pelican.comictv.sources

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.pelican.comictv.archive.copyLimited
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Downloads only. The platform installer must ask the user before installation. */
class ExtensionInstaller(private val context: Context) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()

    suspend fun prepare(entry: ExtensionEntry, fingerprint: String, progress: (Long,Long) -> Unit = {_,_->}): File = withContext(Dispatchers.IO) {
        require(entry.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
        require(fingerprint.matches(Regex("[a-f0-9]{64}")))
        val directory = File(context.cacheDir, "extensions").apply { mkdirs() }
        require(directory.listFiles().orEmpty().filter {it.isFile}.sumOf {it.length()} <= 192L * 1024 * 1024) {
            "Cache de APKs limitado a 256 MiB. Instale ou limpe as baixadas; para arquivos antigos, limpe o cache do Comic TV nas configurações do Android."
        }
        require(directory.usableSpace >= 128L * 1024 * 1024) { "Espaço insuficiente. Libere espaço para manter 64 MiB de reserva durante o download." }
        // Unique files: another finished package may already be open in Android's installer.
        val target = File(directory, "${entry.packageName}-${entry.versionCode}-${java.util.UUID.randomUUID()}.apk")
        var url = entry.apkUrl
        try {
            var downloaded = false
            for (redirect in 0..5) {
                val uri = URI(url)
                require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "O APK deve usar HTTPS." }
                currentCoroutineContext().ensureActive()
                val call = client.newCall(Request.Builder().url(url).build())
                val cancelCall = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { call.cancel() }
                }
                try { call.execute().use { response ->
                    if (response.code in listOf(301, 302, 303, 307, 308)) {
                        check(redirect < 5) { "Redirecionamentos demais no download." }
                        url = uri.resolve(response.header("Location") ?: error("Redirecionamento sem destino.")).toString()
                    } else {
                        check(response.isSuccessful) { "Falha ao baixar extensão: HTTP ${response.code}" }
                        val body = response.body ?: error("APK vazio.")
                        require(body.contentLength() <= 64L * 1024 * 1024) { "A extensão excede 64 MiB." }
                        val expected = body.contentLength()
                        progress(0,expected)
                        var bytes = 0L
                        var last = System.nanoTime()
                        body.byteStream().use { input -> target.outputStream().use { output ->
                            val buffer = ByteArray(16*1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer); if (count < 0) break
                                bytes += count; require(bytes <= 64L*1024*1024) { "A extensão excede 64 MiB." }
                                output.write(buffer,0,count)
                                val now = System.nanoTime()
                                if (now-last >= 100_000_000) { progress(bytes,expected); last=now }
                            }
                        } }
                        progress(bytes,expected)
                        downloaded = true
                    }
                } } finally { cancelCall.cancel() }
                if (downloaded) break
            }
            check(downloaded) { "Não foi possível baixar o APK." }
            currentCoroutineContext().ensureActive()
            verify(target, entry, fingerprint)
            target
        } catch (e: Exception) { target.delete(); throw e }
    }

    internal suspend fun revalidate(file: File, entry: ExtensionEntry, fingerprint: String) = withContext(Dispatchers.IO) {
        require(file.canonicalFile.parentFile == File(context.cacheDir,"extensions").canonicalFile && file.isFile && file.length() <= 64L*1024*1024)
        verify(file,entry,fingerprint)
    }

    @Suppress("DEPRECATION")
    private fun verify(file: File, expected: ExtensionEntry, fingerprint: String) {
        // Read bounded metadata first: PackageManager may reject an APK whose minSdk
        // exceeds this device, obscuring the actual compatibility error.
        ApkRequirements.requireSupported(ApkRequirements.minimumSdk(file), Build.VERSION.SDK_INT)
        val signatureFlag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageArchiveInfo(file.path,
            signatureFlag or PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS) ?: error("O download não é um APK válido.")
        require(info.packageName == expected.packageName) { "O pacote do APK difere do repositório." }
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        require(version == expected.versionCode) { "A versão do APK difere do repositório." }
        require(info.reqFeatures.orEmpty().any { it.name == "tachiyomi.extension" }) { "O APK não é uma extensão Mihon." }
        val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty() else info.signatures.orEmpty()
        val actual = certificates.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }
        require(actual.size == 1 && actual.single() == fingerprint) { "A assinatura do APK não corresponde ao repositório confiável." }
    }
}
