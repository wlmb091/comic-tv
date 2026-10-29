package com.pelican.comictv.library

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HeroCarouselControllerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeControllerAdvancesAndRestartsItsTimerAfterPause() {
        val paused = mutableStateOf(false)
        lateinit var carousel: HeroCarouselState
        compose.mainClock.autoAdvance = false
        compose.setContent {
            carousel = rememberHeroCarousel(listOf("a", "b", "c"), "a", enabled = true, paused = paused.value)
        }
        compose.mainClock.advanceTimeBy(7_900)
        compose.runOnIdle { assertEquals("a", carousel.id) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals("b", carousel.id); paused.value = true }
        compose.mainClock.advanceTimeBy(20_000)
        compose.runOnIdle { assertEquals("b", carousel.id); paused.value = false }
        // Apply the resumed state before measuring its new full interval.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(7_900)
        compose.runOnIdle { assertEquals("b", carousel.id) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals("c", carousel.id) }
    }
}
