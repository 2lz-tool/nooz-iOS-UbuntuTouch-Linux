package xyz.mdhv.riverwip.data.cache

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import xyz.mdhv.riverwip.data.TEST_DIR
import xyz.mdhv.riverwip.data.newFakeFileSystem

class FullTextCacheTest {

    private val fs = newFakeFileSystem()
    private val dir = TEST_DIR

    @Test fun putThenGetRoundTrips() {
        val cache = FullTextCache(fs, dir, maxBytes = 1_000_000)
        cache.put("item1", "hello world")
        assertEquals("hello world", cache.get("item1"))
        assertTrue(cache.contains("item1"))
    }

    @Test fun missingItemReturnsNull() {
        val cache = FullTextCache(fs, dir, maxBytes = 1_000_000)
        assertNull(cache.get("missing"))
        assertTrue(!cache.contains("missing"))
    }

    @Test fun removeDeletesEntry() {
        val cache = FullTextCache(fs, dir, maxBytes = 1_000_000)
        cache.put("item1", "text")
        cache.remove("item1")
        assertNull(cache.get("item1"))
    }

    @Test fun evictsOldestFirstWhenOverBudget() {
        // Each entry ~10 bytes; budget fits 2 entries.
        val cache = FullTextCache(fs, dir, maxBytes = 22)
        cache.put("a", "0123456789") // written first -> oldest
        cache.put("b", "0123456789")
        cache.put("c", "0123456789") // pushes total over budget -> "a" evicted
        assertNull(cache.get("a"))
        assertEquals("0123456789", cache.get("b"))
        assertEquals("0123456789", cache.get("c"))
    }

    @Test fun currentSizeBytesReflectsContents() {
        val cache = FullTextCache(fs, dir, maxBytes = 1_000_000)
        assertEquals(0L, cache.currentSizeBytes())
        cache.put("a", "12345")
        assertEquals(5L, cache.currentSizeBytes())
    }

    @Test fun clearRemovesEverything() {
        val cache = FullTextCache(fs, dir, maxBytes = 1_000_000)
        cache.put("a", "x")
        cache.put("b", "y")
        cache.clear()
        assertEquals(0L, cache.currentSizeBytes())
    }
}
