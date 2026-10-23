// OTAKU: hand-written implementation of the extensions-lib AppInfo surface.
package eu.kanade.tachiyomi

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Suppress("UNUSED")
object AppInfo {
    fun getVersionCode(): Int = packageInfo().let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode }

    fun getVersionName(): String = packageInfo().versionName.orEmpty()

    /** @since extensions-lib 1.5 */
    fun getSupportedImageMimeTypes(): List<String> =
        listOf("image/jpeg", "image/png", "image/gif", "image/webp")

    private fun packageInfo() = Injekt.get<Application>().let { it.packageManager.getPackageInfo(it.packageName, 0) }
}
