package com.pelican.comictv.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.pelican.comictv.sources.ApkRequirements
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile

internal class AndroidAppUpdates(context: Context) : AppUpdateSource {
    private val app = context.applicationContext
    private val directory = File(app.cacheDir, "updates")
    private val http = UpdateHttpClient()
    private val store = UpdateDownloadStore(directory, { app.cacheDir.usableSpace }, ::verify) { update, target, progress ->
        http.download(update.apkUrl, target, update.sizeBytes, progress)
    }
    override suspend fun check(): UpdateManifest? = withContext(Dispatchers.IO) {
        val installed = installedVersionCode()
        store.cleanupInstalled(installed)
        val manifest = UpdateManifestCodec.decode(http.text(UpdateManifest.CHANNEL_URL))
        if (!manifest.isNewerThan(installed)) return@withContext null
        require(manifest.minSdk <= Build.VERSION.SDK_INT) { "A versão publicada exige Android mais recente que este aparelho." }
        manifest
    }
    override suspend fun download(update: UpdateManifest, progress: (Long) -> Unit) = withContext(Dispatchers.IO) { store.download(update, progress) }
    override suspend fun revalidate(file: File, update: UpdateManifest) = withContext(Dispatchers.IO) { store.revalidate(file, update) }
    override suspend fun markOffered(file: File) = withContext(Dispatchers.IO) { store.markOffered(file) }
    override suspend fun installationReturned(file: File) = withContext(Dispatchers.IO) { store.installationReturned(file) }
    @Suppress("DEPRECATION")
    override suspend fun installedVersionCode(): Long = withContext(Dispatchers.IO) { version(app.packageManager.getPackageInfo(app.packageName, 0)) }
    @Suppress("DEPRECATION")
    private suspend fun verify(file: File, expected: UpdateManifest) {
        currentCoroutineContext().ensureActive()
        val sdk = ApkRequirements.minimumSdk(file)
        require(sdk == expected.minSdk && sdk <= Build.VERSION.SDK_INT) { "O requisito Android do APK não corresponde à atualização." }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = app.packageManager.getPackageArchiveInfo(file.path, flags) ?: throw IOException("APK de atualização inválido.")
        val installed = app.packageManager.getPackageInfo(app.packageName, flags)
        require(info.packageName == app.packageName && version(info) == expected.versionCode && info.versionName == expected.versionName) { "O pacote ou a versão do APK não corresponde à atualização." }
        require(version(info) > version(installed)) { "Esta atualização já está instalada ou é mais antiga." }
        val signatures = fingerprints(info)
        require(signatures.size == 1 && signatures == fingerprints(installed)) { "A assinatura do APK não corresponde ao Comic TV instalado." }
        ZipFile(file).use { zip ->
            val abis = linkedSetOf<String>(); var count = 0
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entry = entries.nextElement(); require(++count <= 50_000) { "APK com entradas demais." }
                Regex("lib/([^/]+)/[^/]+\\.so").matchEntire(entry.name)?.let { abis += it.groupValues[1] }
            }
            require(abis.isEmpty() || Build.SUPPORTED_ABIS.any { it in abis }) { "O APK não é compatível com o processador deste aparelho." }
        }
    }
    @Suppress("DEPRECATION")
    private fun version(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun fingerprints(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures).orEmpty().map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
}
