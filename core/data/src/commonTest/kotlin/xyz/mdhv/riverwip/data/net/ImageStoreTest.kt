package xyz.mdhv.riverwip.data.net

import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageStoreTest {
    private val fs = FakeFileSystem()
    private val dir = "/cache/images".toPath()

    @Test fun downloadsOnceThenServesFromDisk() = runTest {
        var fetches = 0
        val store = ImageStore(fs, dir) { fetches++; byteArrayOf(1, 2, 3) }
        assertContentEquals(byteArrayOf(1, 2, 3), store.load("https://x/a.jpg"))
        assertContentEquals(byteArrayOf(1, 2, 3), store.load("https://x/a.jpg"))
        assertEquals(1, fetches)
        // and a fresh store over the same directory (an app restart) still doesn't refetch
        assertContentEquals(byteArrayOf(1, 2, 3), ImageStore(fs, dir) { fetches++; null }.load("https://x/a.jpg"))
        assertEquals(1, fetches)
    }

    @Test fun aFailedFetchIsNullAndNotCached() = runTest {
        var ok = false
        val store = ImageStore(fs, dir) { if (ok) byteArrayOf(9) else null }
        assertNull(store.load("https://x/b.jpg"))
        ok = true
        assertContentEquals(byteArrayOf(9), store.load("https://x/b.jpg"))
    }

    @Test fun evictsOldestBeyondTheBudget() = runTest {
        val store = ImageStore(fs, dir, maxBytes = 25) { ByteArray(10) }
        store.load("https://x/1"); store.load("https://x/2"); store.load("https://x/3")
        assertTrue(store.currentSizeBytes() <= 25, "size ${store.currentSizeBytes()}")
    }
}
