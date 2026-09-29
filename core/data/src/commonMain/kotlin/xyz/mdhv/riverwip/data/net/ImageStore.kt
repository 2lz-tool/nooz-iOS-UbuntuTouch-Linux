package xyz.mdhv.riverwip.data.net

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path

/**
 * A feed's images, fetched once and kept on disk: the offline-friendly half of image loading
 * (decoding and the in-memory cache live with the UI). The disk part is a bounded LRU by
 * modification time, like [xyz.mdhv.riverwip.data.cache.FullTextCache]. Concurrent requests for
 * the same URL share one download.
 */
class ImageStore(
    private val fileSystem: FileSystem,
    private val baseDir: Path,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val fetch: suspend (String) -> ByteArray?,
) {
    private val lock = Mutex()

    private fun fileFor(url: String): Path = baseDir / (url.encodeUtf8().sha256().hex().take(40) + ".img")

    /** The image's encoded bytes from disk, or downloaded and then stored; null if it could not be had. */
    suspend fun load(url: String): ByteArray? {
        val file = fileFor(url)
        readCached(file)?.let { return it }
        val bytes = fetch(url) ?: return null
        lock.withLock {
            try {
                fileSystem.createDirectories(baseDir)
                fileSystem.write(file) { write(bytes) }
                evictIfNeeded()
            } catch (_: Exception) {
                // A full or read-only disk must not stop the image from showing this once.
            }
        }
        return bytes
    }

    private fun readCached(file: Path): ByteArray? = try {
        if (fileSystem.metadataOrNull(file)?.isRegularFile == true) fileSystem.read(file) { readByteArray() } else null
    } catch (_: Exception) {
        null
    }

    fun currentSizeBytes(): Long = files().sumOf { fileSystem.metadataOrNull(it)?.size ?: 0L }

    fun clear() {
        files().forEach { fileSystem.delete(it, mustExist = false) }
    }

    private fun files(): List<Path> = if (fileSystem.exists(baseDir)) fileSystem.list(baseDir) else emptyList()

    private fun evictIfNeeded() {
        var size = currentSizeBytes()
        if (size <= maxBytes) return
        for (f in files().sortedBy { fileSystem.metadataOrNull(it)?.lastModifiedAtMillis ?: 0L }) {
            if (size <= maxBytes) break
            size -= fileSystem.metadataOrNull(f)?.size ?: 0L
            fileSystem.delete(f, mustExist = false)
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 64L * 1024 * 1024
    }
}
