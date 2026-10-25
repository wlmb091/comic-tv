package com.pelican.comictv.sources

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Main-thread viewport updates. Completed misses stay attempted until they leave the viewport. */
internal class VisibleTitlePreviews(private val scope: CoroutineScope,
    private val load: suspend (OnlineTitle) -> Unit) {
    private val jobs = linkedMapOf<String, Job>()
    fun submit(visible: List<OnlineTitle>) {
        val desired = visible.filter { it.coverUrl.isNullOrBlank() }.distinctBy { it.key }.take(40)
        val keys = desired.map { it.key }.toSet()
        jobs.keys.toList().filter { it !in keys }.forEach { jobs.remove(it)?.cancel() }
        for (title in desired) if (title.key !in jobs) jobs[title.key] = scope.launch { load(title) }
    }
    fun close() { jobs.values.forEach { it.cancel() }; jobs.clear() }
}
