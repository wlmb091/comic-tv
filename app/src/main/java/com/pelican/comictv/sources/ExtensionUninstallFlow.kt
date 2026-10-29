package com.pelican.comictv.sources

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal fun canUninstallSource(source: SourceHandle, sources: List<SourceHandle>,
    installed: List<InstalledExtension>, appPackage: String): Boolean =
    source in sources && source.packageName != appPackage &&
        installed.any { it.packageName == source.packageName } &&
        source.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))

/** Android owns confirmation; never hide a package merely because uninstall was requested. */
@Composable internal fun rememberExtensionUninstallFlow(vm: SourcesViewModel): (SourceHandle) -> Unit {
    val context = LocalContext.current
    val current by rememberUpdatedState(vm)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        current.refreshInstalled()
    }
    return { source ->
        val state = current.state.value
        if (canUninstallSource(source, state.sources, state.installed, context.packageName)) {
            try {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(source.packageName, android.content.pm.PackageManager.GET_CONFIGURATIONS)
                require(info.reqFeatures.orEmpty().any { it.name == "tachiyomi.extension" })
                launcher.launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", source.packageName, null)))
            } catch (_: Exception) { current.installationMessage("Não foi possível abrir a desinstalação do Android. A fonte foi mantida.") }
        } else current.installationMessage("Esta fonte não está mais instalada. Atualize a lista.")
    }
}
