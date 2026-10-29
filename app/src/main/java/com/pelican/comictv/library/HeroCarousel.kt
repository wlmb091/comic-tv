package com.pelican.comictv.library

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** IDs only: never writes reading history, library focus or a scroll position. */
internal object HeroCarouselPolicy {
    const val INTERVAL_MS = 8_000L
    fun current(ids: List<String>, selected: String?, fallback: String?): String? =
        selected?.takeIf { it in ids } ?: fallback?.takeIf { it in ids } ?: ids.firstOrNull()
    fun next(ids: List<String>, selected: String?): String? {
        if (ids.isEmpty()) return null
        return ids[(ids.indexOf(selected).coerceAtLeast(0) + 1) % ids.size]
    }
    fun due(elapsedMs: Long, running: Boolean, count: Int) =
        running && count > 1 && elapsedMs >= INTERVAL_MS
    fun indicatorIndices(count: Int, selected: Int): List<Int> {
        if (count <= 0) return emptyList()
        val shown = count.coerceAtMost(7)
        val start = (selected - shown / 2).coerceIn(0, count - shown)
        return (start until start + shown).toList()
    }
}

@Immutable internal data class HeroCarouselInfo(
    val id: String? = null, val index: Int = 0, val count: Int = 0,
    val progress: State<Float> = EmptyHeroProgress, val running: Boolean = false,
    val enabled: Boolean = false,
)
private val EmptyHeroProgress: State<Float> = mutableFloatStateOf(0f)
internal val LocalHeroCarouselInfo = staticCompositionLocalOf { HeroCarouselInfo() }
internal val LocalHeroCarouselProgress = staticCompositionLocalOf<State<Float>> { EmptyHeroProgress }

internal class HeroCarouselState(val info: HeroCarouselInfo, val select: (String) -> Unit) {
    val id get() = info.id
}

@Composable internal fun rememberHeroCarousel(
    ids: List<String>, initialId: String?, enabled: Boolean, paused: Boolean,
): HeroCarouselState {
    var selected by rememberSaveable { mutableStateOf(initialId) }
    var selectionEpoch by remember { mutableIntStateOf(0) }
    val current = HeroCarouselPolicy.current(ids, selected, initialId)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val running = enabled && !paused && resumed && ids.size > 1
    // Pause, manual selection and data changes restart a full interval, as in 1.2.8.
    // This timer intentionally does not use animation frames or Android's animation scale.
    LaunchedEffect(ids, current, running, selectionEpoch) {
        awaitHeroCarouselAdvance(ids, current, running)?.let { selected = it }
    }
    return HeroCarouselState(HeroCarouselInfo(current, ids.indexOf(current).coerceAtLeast(0), ids.size,
        EmptyHeroProgress, running, enabled)) { selected = it; selectionEpoch++ }
}

/** A cancellable timer shared by the live controller and virtual-time regression tests. */
internal suspend fun awaitHeroCarouselAdvance(ids: List<String>, current: String?, running: Boolean): String? {
    if (!running || ids.size < 2) return null
    delay(HeroCarouselPolicy.INTERVAL_MS)
    return HeroCarouselPolicy.next(ids, current)
}
