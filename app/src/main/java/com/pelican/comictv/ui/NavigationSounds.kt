package com.pelican.comictv.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pelican.comictv.R

/** Clicks on the remote's keys. The sounds are small files made for this app, loaded once so a key press never waits on disk. */
internal class NavigationSounds private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("navigation-sounds", Context.MODE_PRIVATE)
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val moves = listOf(R.raw.ui_move, R.raw.ui_move_2, R.raw.ui_move_3, R.raw.ui_move_4, R.raw.ui_move_5).map { pool.load(context, it, 1) }
    private val confirms = listOf(R.raw.ui_ok, R.raw.ui_ok_2, R.raw.ui_ok_3, R.raw.ui_ok_4, R.raw.ui_ok_5).map { pool.load(context, it, 1) }
    private var enabledState by mutableStateOf(preferences.getBoolean("enabled", true))
    private var volumeState by mutableIntStateOf(preferences.getInt("volume", 50).coerceIn(0, 100))
    private var styleState by mutableIntStateOf(preferences.getInt("style", 0).coerceIn(0, 4))
    private var lastMove = 0L
    var enabled: Boolean
        get() = enabledState
        set(value) { enabledState = value; preferences.edit().putBoolean("enabled", value).apply() }
    var volumePercent: Int
        get() = volumeState
        set(value) { volumeState = value.coerceIn(0, 100); preferences.edit().putInt("volume", volumeState).apply() }
    var style: Int
        get() = styleState
        set(value) { styleState = value.coerceIn(0, 4); preferences.edit().putInt("style", styleState).apply() }
    private fun play(sound: Int) { val volume = volumePercent / 100f; pool.play(sound, volume, volume, 1, 0, 1f) }
    fun preview() { if (enabled) play(confirms[style]) }
    fun onKey(event: KeyEvent) {
        // Commands from the phone are virtual keys: the sound belongs to the remote in the hand.
        if (event.action != KeyEvent.ACTION_DOWN || event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD || !enabled) return
        when (navigationSoundOf(event.keyCode, event.repeatCount)) {
            NavigationSound.Confirm -> play(confirms[style])
            NavigationSound.Move -> {
                // A held arrow repeats fast; the click follows at a rate the ear can separate.
                val now = SystemClock.uptimeMillis()
                if (now - lastMove >= 90L) { lastMove = now; play(moves[style]) }
            }
            null -> Unit
        }
    }
    companion object {
        @Volatile private var instance: NavigationSounds? = null
        fun of(context: Context): NavigationSounds = instance ?: synchronized(this) {
            instance ?: NavigationSounds(context.applicationContext).also { instance = it }
        }
        /** Hears the keys of one window, the app's or a dialog's, and lets them go on as before. */
        fun attach(window: Window) {
            val sounds = of(window.context)
            val original = window.callback ?: return
            window.callback = object : Window.Callback by original {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    sounds.onKey(event)
                    return original.dispatchKeyEvent(event)
                }
            }
        }
        val NAMES = listOf("Clique", "Bolha", "Madeira", "Cristal", "Retrô")
        fun stepVolume(percent: Int, delta: Int): Int = (percent + delta * 5).coerceIn(0, 100)
    }
}

internal enum class NavigationSound { Move, Confirm }

/** OK sounds once per press; holding it opens options and must not repeat the sound. */
internal fun navigationSoundOf(keyCode: Int, repeatCount: Int): NavigationSound? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (repeatCount == 0) NavigationSound.Confirm else null
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> NavigationSound.Move
    KeyEvent.KEYCODE_BACK -> if (repeatCount == 0) NavigationSound.Move else null
    else -> null
}
