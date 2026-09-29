package xyz.mdhv.riverwip.inference

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import xyz.mdhv.riverwip.inference.byok.ByokConfig
import xyz.mdhv.riverwip.inference.byok.ByokConfigStore
import xyz.mdhv.riverwip.inference.byok.FileKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByokStoreTest {
    private val fs = FakeFileSystem()
    private val path = "/home/u/.local/share/nooz/byok.json".toPath()

    @Test fun roundTripsAndTrims() {
        val store = ByokConfigStore(FileKeyValueStore(fs, path))
        assertFalse(store.isConfigured)
        store.save(ByokConfig(" https://api.example.com/v1/ ", " sk-1 ", " gpt "))
        val loaded = ByokConfigStore(FileKeyValueStore(fs, path)).load() // a fresh store reads the same file
        assertEquals(ByokConfig("https://api.example.com/v1/", "sk-1", "gpt"), loaded)
        assertTrue(loaded.isComplete)
        assertEquals("https://api.example.com/v1/chat/completions", loaded.chatCompletionsUrl)
    }

    @Test fun clearForgetsEverything() {
        val store = ByokConfigStore(FileKeyValueStore(fs, path))
        store.save(ByokConfig("u", "k", "m"))
        store.clear()
        assertFalse(store.isConfigured)
        assertEquals(ByokConfig(), store.load())
    }

    @Test fun aCorruptFileReadsAsEmptyRatherThanCrashing() {
        fs.createDirectories(path.parent!!)
        fs.write(path) { writeUtf8("{not json") }
        assertEquals(ByokConfig(), ByokConfigStore(FileKeyValueStore(fs, path)).load())
    }
}
