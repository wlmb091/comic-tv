package com.pelican.comictv.updates

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

@Composable internal fun rememberAppUpdateInstallFlow(model: AppUpdateViewModel): (File) -> Unit {
    val current by rememberUpdatedState(model)
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pending = null; current.installationReturned()
    }
    val launch: (File) -> Unit = { file ->
        try {
            require(file.canonicalFile == file.absoluteFile && file.parentFile == File(context.cacheDir, "updates").canonicalFile && file.isFile)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            installer.launch(Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_RETURN_RESULT, true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { pending = null; current.installationReturned("Não foi possível abrir o instalador. Você pode tentar novamente.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = pending?.let(::File)
        if (file != null && (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls())) current.continueInstall(file, launch)
        else { pending = null; current.installationReturned("Autorize o Comic TV a instalar aplicativos nas configurações do Android para continuar.") }
    }
    return { file ->
        pending = file.path
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
            catch (_: Exception) { pending = null; current.installationReturned("Abra as configurações do Android e autorize o Comic TV a instalar aplicativos.") }
        } else launch(file)
    }
}
