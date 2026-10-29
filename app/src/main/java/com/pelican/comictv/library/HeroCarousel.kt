package com.pelican.comictv.library

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** IDs only: never writes reading history, library focus or a scroll position. */
internal object HeroCarouselPolicy {
    const val INTERVAL_MS = 5_000L
    fun current(ids: List<String>, selected: String?, fallback: String?): String? =
        selected?.takeIf { it in ids } ?: fallback?.takeIf { it in ids } ?: ids.firstOrNull()
    fun next(ids: List<String>, selected: String?): String? {
        if (ids.isEmpty()) return null
        return ids[(ids.indexOf(selected).coerceAtLeast(0) + 1) % ids.size]
    }
    fun due(elapsedMs: Long, running: Boolean, count: Int) =
        running && count > 1 && elapsedMs >= INTERVAL_MS
}

internal class HeroCarouselState(val id: String?, val select: (String) -> Unit)

@Composable internal fun rememberHeroCarousel(
    ids: List<String>, initialId: String?, enabled: Boolean, paused: Boolean,
): HeroCarouselState {
    var selected by rememberSaveable { mutableStateOf(initialId) }
    val current = HeroCarouselPolicy.current(ids, selected, initialId)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Every pause/manual selection/data change restarts the full interval; no catch-up ticks.
    LaunchedEffect(ids, current, enabled, paused, resumed) {
        if (HeroCarouselPolicy.due(HeroCarouselPolicy.INTERVAL_MS, enabled && !paused && resumed, ids.size)) {
            delay(HeroCarouselPolicy.INTERVAL_MS)
            selected = HeroCarouselPolicy.next(ids, current)
        }
    }
    return HeroCarouselState(current) { selected = it }
}
