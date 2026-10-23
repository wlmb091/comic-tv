package com.pelican.comictv

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pelican.comictv.ui.ComicTvApp
import com.pelican.comictv.ui.ComicTvTheme
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache

class ComicTvApplication : Application(), SingletonImageLoader.Factory {
    val container by lazy { AppContainer(this) }
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizeBytes(32L * 1024 * 1024).build() }
        .build()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { ComicTvTheme { ComicTvApp((application as ComicTvApplication).container) } }
    }
}
