package com.pelican.comictv.ui

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class NavigationSoundsTest {
    @Test fun okSoundsDifferentAndOnlyOncePerPress() {
        assertEquals(NavigationSound.Confirm, navigationSoundOf(KeyEvent.KEYCODE_DPAD_CENTER, 0))
        assertEquals(NavigationSound.Confirm, navigationSoundOf(KeyEvent.KEYCODE_ENTER, 0))
        assertNull(navigationSoundOf(KeyEvent.KEYCODE_DPAD_CENTER, 3))
    }
    @Test fun arrowsAndBackClick() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertEquals(NavigationSound.Move, navigationSoundOf(key, 2))
        assertEquals(NavigationSound.Move, navigationSoundOf(KeyEvent.KEYCODE_BACK, 0))
        assertNull(navigationSoundOf(KeyEvent.KEYCODE_VOLUME_UP, 0))
    }
    @Test fun volumeGoesRound() {
        assertEquals(55, NavigationSounds.stepVolume(50, 1))
        assertEquals(100, NavigationSounds.stepVolume(100, 1))
        assertEquals(0, NavigationSounds.stepVolume(0, -1))
    }
}
