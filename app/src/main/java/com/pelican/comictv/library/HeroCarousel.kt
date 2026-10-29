package com.pelican.comictv.library

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pelican.comictv.ui.TvColors

/** IDs only: never writes reading history, library focus or a scroll position. */
internal object HeroCarouselPolicy {
    const val INTERVAL_MS = 10_000L
    fun current(ids: List<String>, selected: String?, fallback: String?): String? =
        selected?.takeIf { it in ids } ?: fallback?.takeIf { it in ids } ?: ids.firstOrNull()
    fun next(ids: List<String>, selected: String?): String? {
        if (ids.isEmpty()) return null
        return ids[(ids.indexOf(selected).coerceAtLeast(0) + 1) % ids.size]
    }
    fun due(elapsedMs: Long, running: Boolean, count: Int) =
        running && count > 1 && elapsedMs >= INTERVAL_MS
    fun motion(style: String, index: Int): String = if (style == "Dynamic")
        listOf("Pan", "ZoomIn", "ZoomOut")[Math.floorMod(index, 3)] else style
    fun zoom(style: String, progress: Float): Float = when (style) {
        "ZoomIn" -> 1f + .08f * progress.coerceIn(0f, 1f)
        "ZoomOut" -> 1.08f - .08f * progress.coerceIn(0f, 1f)
        else -> 1f
    }
    fun progress(elapsedMs: Long): Float = (elapsedMs.toFloat() / INTERVAL_MS).coerceIn(0f, 1f)
    // Crop alignment moves the viewport inside the original image; it never exposes empty pixels.
    fun verticalBias(progress: Float): Float = -progress.coerceIn(0f, 1f)
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
    val progress = remember(ids, current, selectionEpoch) { mutableFloatStateOf(0f) }
    var elapsed by remember(ids, current, selectionEpoch) { mutableLongStateOf(0L) }
    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val motionEnabled = enabled && motionScale > 0f && !TvColors.options.highContrast
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val running = motionEnabled && !paused && resumed && ids.size > 1
    // One monotonic clock drives the next edition, image framing and active marker together.
    // Pausing freezes all three; a new edition/manual selection resets the clock. No catch-up ticks.
    LaunchedEffect(ids, current, running, selectionEpoch) {
        if (running) {
            val started = withFrameNanos { it }
            val resumedAt = elapsed
            do {
                val elapsedMs = resumedAt + withFrameNanos { (it - started) / 1_000_000L }
                elapsed = elapsedMs
                progress.floatValue = HeroCarouselPolicy.progress(elapsedMs)
                if (HeroCarouselPolicy.due(elapsedMs, true, ids.size)) {
                    selected = HeroCarouselPolicy.next(ids, current)
                    break
                }
            } while (true)
        }
    }
    return HeroCarouselState(HeroCarouselInfo(current, ids.indexOf(current).coerceAtLeast(0), ids.size,
        progress, running, motionEnabled)) { selected = it; selectionEpoch++ }
}
