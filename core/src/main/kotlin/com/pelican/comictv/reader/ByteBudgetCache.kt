package com.pelican.comictv.reader

/** Eviction drops references; renderers decide object lifetime, never recycle a displayed frame. */
class ByteBudgetCache<K, V>(private val budget: Long, private val weight: (V) -> Long) {
    private val entries = LinkedHashMap<K, V>(32, .75f, true)
    var bytes: Long = 0; private set
    init { require(budget > 0) }
    @Synchronized fun get(key: K): V? = entries[key]
    @Synchronized fun put(key: K, value: V) {
        entries.remove(key)?.let { bytes -= weight(it) }
        val size = weight(value)
        require(size >= 0)
        if (size > budget) return
        entries[key] = value; bytes += size
        val iterator = entries.entries.iterator()
        while (bytes > budget && iterator.hasNext()) { bytes -= weight(iterator.next().value); iterator.remove() }
    }
    @Synchronized fun clear() { entries.clear(); bytes = 0 }
}
