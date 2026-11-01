package com.pelican.comictv

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pelican.comictv.ui.LocalAppOrientation
import com.pelican.comictv.ui.OrientedRootLayout
import com.pelican.comictv.ui.ComicTvApp
import com.pelican.comictv.ui.ComicTvTheme
import com.pelican.comictv.ui.AppExitGuard
import com.pelican.comictv.companion.CompanionDialogs
import com.pelican.comictv.companion.LocalCompanionDialogs
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache

class ComicTvApplication : Application(), SingletonImageLoader.Factory {
    val container by lazy { AppContainer(this) }
    override fun newImageLoader(context: Context): ImageLoader {
        val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val budget=com.pelican.comictv.reader.imageMemoryMiB(manager?.memoryClass ?: 192,manager?.isLowRamDevice ?: false)
        return ImageLoader.Builder(context)
            .memoryCache {MemoryCache.Builder().maxSizeBytes(budget.toLong()*1024*1024).build()}.build()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            @Suppress("DEPRECATION")
            val display = android.util.DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
            val configuration = resources.configuration
            android.util.Log.i("ComicTvDisplay", "realPx=${display.widthPixels}x${display.heightPixels}; " +
                "densityDpi=${display.densityDpi}; appDp=${configuration.screenWidthDp}x${configuration.screenHeightDp}; " +
                "fontScale=${configuration.fontScale}")
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val root = OrientedRootLayout(this)
        // Applied before the first frame: a saved portrait must not flash a landscape layout.
        root.degrees = (application as ComicTvApplication).container.orientation.state.value.degrees
        val composeView = ComposeView(this)
        root.addView(composeView)
        setContentView(root)
        composeView.setContent {
            val container = (application as ComicTvApplication).container
            val appearance by container.appearance.state.collectAsStateWithLifecycle()
            val orientation by container.orientation.state.collectAsStateWithLifecycle()
            SideEffect { root.degrees = orientation.degrees }
            // Above the exit guard: its confirmation window must reach the same phone control as the app's dialogs.
            val companionDialogs = remember { CompanionDialogs() }
            CompositionLocalProvider(LocalCompanionDialogs provides companionDialogs, LocalAppOrientation provides orientation) {
                ComicTvTheme(appearance) { AppExitGuard(onExit = { finish() }) { ComicTvApp(container) } }
            }
        }
    }
}
