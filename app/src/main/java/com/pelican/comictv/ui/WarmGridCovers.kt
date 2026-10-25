package com.pelican.comictv.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.pelican.comictv.reader.coverWarmIndices
import com.pelican.comictv.reader.imageMemoryMiB
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect

internal data class CoverWarmEntry(val key: String, val data: Any?)
private data class CoverViewport(val first: Int, val last: Int, val width: Int)

/** Same loader/keys as visible covers. Never changes the visible request's resolution or the original. */
@Composable internal fun WarmGridCovers(grid: LazyGridState, entries: List<CoverWarmEntry>) {
    val app=LocalContext.current.applicationContext
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(grid,entries,app,lifecycle) {
        if(entries.isEmpty()) return@LaunchedEffect
        val index=entries.mapIndexed {position,entry->entry.key to position}.toMap()
        val manager=app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val limit=if(imageMemoryMiB(manager?.memoryClass ?: 192,manager?.isLowRamDevice ?: false)<=24) 1 else 3
        val loader=SingletonImageLoader.get(app)
        fun viewport(): CoverViewport? {
            val items=grid.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                (item.key as? String)?.let {index[it]}?.let {it to item.size.width}
            }
            if(items.isEmpty()) return null
            return CoverViewport(items.minOf{it.first},items.maxOf{it.first},items.maxOf{it.second}.coerceIn(1,640))
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous=-1
            snapshotFlow {viewport()}.filterNotNull().collect {
                delay(120) // Do not start network/decodes for every transient position while navigating.
                val current=viewport() ?: return@collect
                val direction=if(previous>=0 && current.first<previous) -1 else 1
                previous=current.first
                for(position in coverWarmIndices(entries.size,current.first,current.last,direction,limit)) {
                    if(viewport()!=current) break
                    val data=entries[position].data ?: continue
                    try {
                        loader.execute(ImageRequest.Builder(app).data(data)
                            .size(current.width,current.width*3/2+1).build())
                    } catch(e:CancellationException) {throw e} catch(_:Exception) { }
                    // Finish one active request before following a new viewport; do not repeatedly abort
                    // the image just as it enters the visible grid. Lifecycle/disposal still cancels it.
                }
            }
        }
    }
}
