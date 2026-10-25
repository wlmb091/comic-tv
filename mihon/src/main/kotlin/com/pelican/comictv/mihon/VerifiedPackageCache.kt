package com.pelican.comictv.mihon

internal data class VerifiedPackageStamp(val version: Long, val pins: Set<String>, val updatedAt: Long,
    val apkPaths: List<String>, val classes: List<String>, val api: String)

/** Trust precedes the fast path; rejected/failed replacements cannot retain old package state. */
internal class VerifiedPackageCache<T>(private val invalidateState: (String) -> Unit) {
    private data class Entry<T>(val stamp: VerifiedPackageStamp, val value: T)
    private val entries = mutableMapOf<String, Entry<T>>()
    @Synchronized fun load(packageName: String, stamp: VerifiedPackageStamp, pin: String, create: () -> T): T {
        try {
            ExtensionPolicy.requireTrusted(stamp.pins, pin)
            entries[packageName]?.takeIf { it.stamp == stamp }?.let { return it.value }
            evict(packageName)
            val value = create()
            entries[packageName] = Entry(stamp, value)
            return value
        } catch (failure: Throwable) { evict(packageName); throw failure }
    }
    @Synchronized fun evict(packageName: String) { entries.remove(packageName); invalidateState(packageName) }
    @Synchronized fun <R> read(packageName: String, lookup: () -> R): R =
        try { lookup() } catch (failure: Throwable) { evict(packageName); throw failure }
}
