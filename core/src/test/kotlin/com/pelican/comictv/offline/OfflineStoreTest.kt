package com.pelican.comictv.offline

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfflineStoreTest {
    private val id = "online-" + "a".repeat(64)
    @Test fun partialCannotBeReadAndResumeReusesOnlySameList() {
        val root = Files.createTempDirectory("offline").toFile()
        try {
            val store = OfflineStore(root, freeBytes = { Long.MAX_VALUE })
            store.begin(id, 2, "b".repeat(64))
            store.incoming(id,0).writeBytes(byteArrayOf(1,2,3)); store.commitPage(id,0,10,20)
            assertFalse(store.isComplete(id))
            assertThrows(Exception::class.java) { store.verified(id) }
            assertEquals(1, OfflineStore(root, freeBytes = { Long.MAX_VALUE }).begin(id,2,"b".repeat(64)).pages.size)
            assertEquals(0, store.begin(id,2,"c".repeat(64)).pages.size)
        } finally { root.deleteRecursively() }
    }
    @Test fun completeIsAtomicChecksIntegrityAndIsSeparateFromOtherData() {
        val root = Files.createTempDirectory("offline-full").toFile()
        try {
            val keep = File(root,"other").apply { writeText("keep") }
            val store = OfflineStore(File(root,"downloads"), freeBytes = { Long.MAX_VALUE })
            store.begin(id,1,"b".repeat(64)); store.incoming(id,0).writeBytes(byteArrayOf(1,2,3))
            store.commitPage(id,0,10,20); store.finish(id)
            assertTrue(store.isComplete(id)); assertEquals(3L, store.verified(id).pages.single().bytes)
            assertThrows(Exception::class.java) { store.begin(id,1,"c".repeat(64)) }
            val damaged = store.page(id,0)
            damaged.writeBytes(byteArrayOf(9,9,9)); damaged.setLastModified(damaged.lastModified()+1000)
            assertFalse("same-sized corruption must remove offline availability",store.isComplete(id))
            assertThrows(Exception::class.java) { store.verified(id) }
            assertEquals(0,store.begin(id,1,"b".repeat(64)).pages.size)
            store.remove(id); assertTrue(keep.isFile); assertFalse(store.isComplete(id))
        } finally { root.deleteRecursively() }
    }
    @Test fun spaceSizeAndPathsAreValidated() {
        val root = Files.createTempDirectory("offline-limits").toFile()
        try {
            val low = OfflineStore(root, freeBytes = { 0 })
            assertThrows(Exception::class.java) { low.begin(id,1,"b".repeat(64)) }
            val store = OfflineStore(root, freeBytes = { Long.MAX_VALUE })
            assertThrows(Exception::class.java) { store.begin("../bad",1,"b".repeat(64)) }
            store.begin(id,1,"b".repeat(64)); store.incoming(id,0).writeBytes(byteArrayOf(1))
            assertThrows(Exception::class.java) { store.commitPage(id,0,0,20) }
            assertThrows(Exception::class.java) { store.commitPage(id,0,100001,100001) }
            assertThrows(Exception::class.java) { store.finish(id) }
        } finally { root.deleteRecursively() }
    }
}
