package xyz.mdhv.riverwip.data.cache

import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * File-based LRU cache for extracted full text (brief §4: "full text is an LRU
 * cache with a user-visible storage budget"). Keyed by item id; values are plain
 * UTF-8 text files. When the budget is exceeded, the least-recently-*written*
 * files are evicted first (approximated via the file's modification time).
 *
 * No platform types: the caller resolves the platform cache directory and file
 * system and passes them in, so this class is unit-tested against a fake file system.
 */
class FullTextCache(
    private val fileSystem: FileSystem,
    private val baseDir: Path,
    private val maxBytes: Long,
) {

    init { fileSystem.createDirectories(baseDir) }

    private fun fileFor(itemId: String): Path = baseDir / "$itemId.txt"

    private fun files(): List<Path> = if (fileSystem.exists(baseDir)) fileSystem.list(baseDir) else emptyList()

    fun get(itemId: String): String? {
        val f = fileFor(itemId)
        return if (contains(itemId)) fileSystem.source(f).buffer().use { it.readUtf8() } else null
    }

    fun put(itemId: String, text: String) {
        fileSystem.sink(fileFor(itemId)).buffer().use { it.writeUtf8(text) }
        evictIfNeeded()
    }

    fun remove(itemId: String) {
        fileSystem.delete(fileFor(itemId), mustExist = false)
    }

    fun contains(itemId: String): Boolean = fileSystem.metadataOrNull(fileFor(itemId))?.isRegularFile == true

    /** Current cache size — surfaced in the storage-budget UI (brief §4). */
    fun currentSizeBytes(): Long = files().sumOf { fileSystem.metadataOrNull(it)?.size ?: 0L }

    fun clear() {
        files().forEach { fileSystem.delete(it, mustExist = false) }
    }

    private fun evictIfNeeded() {
        var size = currentSizeBytes()
        if (size <= maxBytes) return
        val oldestFirst = files().sortedBy { fileSystem.metadataOrNull(it)?.lastModifiedAtMillis ?: 0L }
        for (f in oldestFirst) {
            if (size <= maxBytes) break
            size -= fileSystem.metadataOrNull(f)?.size ?: 0L
            fileSystem.delete(f, mustExist = false)
        }
    }
}
