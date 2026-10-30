package com.pelican.comictv.explorer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.pelican.comictv.archive.ExplorerFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** A local user-selected APK never enters extension/update trust or the comic importer. */
internal object ExplorerApk {
    suspend fun prepare(context: Context, uri: Uri): File = runInterruptible(Dispatchers.IO) {
        val directory = File(context.cacheDir, "explorer-installs").apply { mkdirs() }
        val target = File(directory, "selected.apk")
        val temporary = File(directory, "selected.part")
        val reserve = 64L * 1024 * 1024
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 1024L * 1024 * 1024) { "APK excede o limite de 1 GiB." }
                        check(directory.usableSpace >= reserve + count) { "Espaço insuficiente para preparar a instalação." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Não foi possível abrir o APK.")
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(temporary.path, 0)
            check(info != null) { "Este arquivo não é um APK Android válido. Pacotes divididos não são suportados." }
            check(!target.exists() || target.delete()) { "Não foi possível substituir a preparação anterior." }
            check(temporary.renameTo(target)) { "Não foi possível preparar a instalação." }
            target
        } finally { temporary.delete() }
    }
    fun permission(context: Context): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"))
    fun install(context: Context, file: File): Intent {
        val root = File(context.cacheDir, "explorer-installs")
        require(ExplorerFiles.inside(root, file) && file.name == "selected.apk" && file.isFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.explorer", file)
        @Suppress("DEPRECATION")
        return Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }
}
