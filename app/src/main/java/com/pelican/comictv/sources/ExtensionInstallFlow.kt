package com.pelican.comictv.sources

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** Both unknown-source authorization and installation remain Android system screens. */
@Composable
fun rememberExtensionInstallFlow(getViewModel: () -> SourcesViewModel): (File) -> Unit {
    val model by rememberUpdatedState(getViewModel)
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model().extensionInstallationReturned(pending?.let(::File),true)
        pending = null
        model().refreshInstalled()
    }
    val launchInstaller: (File) -> Unit = { file ->
        try {
            val directory = File(context.cacheDir, "extensions").canonicalFile
            require(file.canonicalFile.parentFile == directory && file.isFile)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.extensions", file)
            installer.launch(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_RETURN_RESULT,true)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            model().extensionInstallationReturned(file,false)
            pending = null
            model().installationMessage("Não foi possível abrir o instalador do Android. ${e.message.orEmpty().take(150)}")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = pending?.let(::File)
        if (file != null && (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls())) {
            launchInstaller(file)
        } else {
            model().extensionInstallationReturned(file,false)
            pending = null
            model().installationMessage("Instalação não autorizada. Você pode tentar novamente na extensão.")
        }
    }
    return { file ->
        pending = file.path
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            try {
                permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } catch (e: Exception) {
                model().extensionInstallationReturned(file,false)
                pending = null
                model().installationMessage("Abra as configurações da TV e autorize o Comic TV a instalar extensões.")
            }
        } else launchInstaller(file)
    }
}
