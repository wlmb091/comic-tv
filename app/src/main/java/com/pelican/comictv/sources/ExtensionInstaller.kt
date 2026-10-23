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

    suspend fun prepare(entry: ExtensionEntry, fingerprint: String): File = withContext(Dispatchers.IO) {
        require(entry.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
        require(fingerprint.matches(Regex("[a-f0-9]{64}")))
        val directory = File(context.cacheDir, "extensions").apply { mkdirs() }
        // Keep only one completed installer package; old provider URIs are no longer offered.
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        val target = File(directory, "${entry.packageName}-${entry.versionCode}.apk")
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
                        body.byteStream().use { copyLimited(it, target, 64L * 1024 * 1024) }
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

    @Suppress("DEPRECATION")
    private fun verify(file: File, expected: ExtensionEntry, fingerprint: String) {
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
