package xyz.mdhv.riverwip.inference.local

import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelManagerTest {

    private val fs = FileSystem.SYSTEM

    private fun tempFile(text: String) = Files.createTempFile("model-test", ".bin").toFile().apply { writeText(text) }

    @Test fun checksumMatchesKnownContent() {
        val file = tempFile("hello world")
        val path = file.absolutePath.toPath()
        val expected = MessageDigest.getInstance("SHA-256").digest("hello world".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, ChecksumVerifier.sha256Hex(fs, path))
        assertTrue(ChecksumVerifier.verify(fs, path, expected))
        assertFalse(ChecksumVerifier.verify(fs, path, "0".repeat(64)))
        file.delete()
    }

    @Test fun blankExpectedChecksumNeverVerifies() {
        val file = tempFile("x")
        assertFalse(ChecksumVerifier.verify(fs, file.absolutePath.toPath(), ""))
        file.delete()
    }

    @Test fun storageBudgetRespectsMargin() {
        val size = 2_600_000_000L
        assertTrue(StorageBudget.canDownload(size, availableBytes = size + 1_000_000_000L))
        assertFalse(StorageBudget.canDownload(size, availableBytes = size)) // no margin left
        assertFalse(StorageBudget.canDownload(size, availableBytes = size / 2))
    }

    @Test fun humanReadableSizes() {
        assertEquals("512 B", StorageBudget.humanReadable(512))
        assertEquals("1.0 KB", StorageBudget.humanReadable(1024))
        assertEquals("2.5 GB", StorageBudget.humanReadable((2.5 * 1024 * 1024 * 1024).toLong()))
    }
}
