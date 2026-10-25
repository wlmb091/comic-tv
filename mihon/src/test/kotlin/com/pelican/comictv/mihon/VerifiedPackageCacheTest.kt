package com.pelican.comictv.mihon

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class VerifiedPackageCacheTest {
    private val pin = "a".repeat(64)
    private fun stamp(version: Long = 1, updated: Long = 1, path: String = "apk") =
        VerifiedPackageStamp(version, setOf(pin), updated, listOf(path), listOf("Class"), "1.4")
    @Test fun lookupDuringReloadCannotEvictTheNewlyConstructedPackage() {
        val loaded = AtomicReference<Any?>(null)
        val cache = VerifiedPackageCache<Any> { loaded.set(null) }
        cache.load("pkg", stamp(), pin) { Any().also { loaded.set(it) } }
        val building = CountDownLatch(1); val release = CountDownLatch(1); val reading = CountDownLatch(1)
        val lookupDone = CountDownLatch(1)
        val fresh = Any()
        val loaderResult = AtomicReference<Result<Any>?>()
        val readerResult = AtomicReference<Result<Any>?>()
        val loader = Thread {
            loaderResult.set(runCatching { cache.load("pkg", stamp(2), pin) {
                building.countDown(); check(release.await(3, TimeUnit.SECONDS)); loaded.set(fresh); fresh
            } })
        }.apply { isDaemon = true }
        val reader = Thread {
            reading.countDown()
            readerResult.set(runCatching { cache.read("pkg") { loaded.get() ?: error("Missing during rebuild") } })
            lookupDone.countDown()
        }.apply { isDaemon = true }
        loader.start()
        try {
            assertTrue(building.await(3, TimeUnit.SECONDS)); reader.start()
            assertTrue(reading.await(3, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (reader.state != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.sleep(1)
            assertEquals(Thread.State.BLOCKED, reader.state)
            assertEquals(1L, lookupDone.count)
        } finally { release.countDown(); loader.join(3000); reader.join(3000) }
        assertSame(fresh, loaderResult.get()!!.getOrThrow())
        assertSame(fresh, readerResult.get()!!.getOrThrow())
        assertSame(fresh, loaded.get())
        assertSame(fresh, cache.load("pkg", stamp(2), pin) { error("Fresh package was evicted") })
    }
    @Test fun sameValidatedIdentityReusesInstanceAndRetainsMemo() {
        var clears = 0; var builds = 0
        val cache = VerifiedPackageCache<Any> { clears++ }
        val first = cache.load("pkg", stamp(), pin) { builds++; Any() }
        assertSame(first, cache.load("pkg", stamp(), pin) { builds++; Any() })
        assertEquals(1, builds); assertEquals(1, clears)
    }
    @Test fun packageUpdateIncludingSameVersionOrPathChangeRebuilds() {
        var builds = 0
        val cache = VerifiedPackageCache<Any> {}
        fun load(stamp: VerifiedPackageStamp) = cache.load("pkg", stamp, pin) { builds++; Any() }
        val first = load(stamp())
        assertNotSame(first, load(stamp(updated = 2)))
        load(stamp(version = 2)); load(stamp(version = 2, path = "new-apk"))
        assertEquals(4, builds)
    }
    @Test fun rejectedTrustOrFailedBuildCannotRetainOlderInstance() {
        var builds = 0
        val cache = VerifiedPackageCache<Any> {}
        val old = cache.load("pkg", stamp(), pin) { builds++; Any() }
        try { cache.load("pkg", stamp(), "b".repeat(64)) { error("Must reject before building") }; fail() } catch (_: IllegalArgumentException) { }
        assertNotSame(old, cache.load("pkg", stamp(), pin) { builds++; Any() })
        try { cache.load("pkg", stamp(2), pin) { error("broken update") }; fail() } catch (_: IllegalStateException) { }
        cache.load("pkg", stamp(), pin) { builds++; Any() }
        assertEquals(3, builds)
    }
}
