package com.pelican.comictv.reader
import org.junit.Assert.*
import org.junit.Test
class ByteBudgetCacheTest {
    @Test fun evictsLeastRecentlyUsedAndNeverExceedsBudget() {
        val cache = ByteBudgetCache<String, Int>(10) { it.toLong() }
        cache.put("a", 4); cache.put("b", 4); assertEquals(4, cache.get("a"))
        cache.put("c", 4)
        assertNull(cache.get("b")); assertEquals(8L, cache.bytes)
        cache.put("a", 6); assertEquals(10L, cache.bytes)
        cache.put("oversize", 100); assertEquals(10L, cache.bytes)
        cache.clear(); assertEquals(0L, cache.bytes)
    }
}
