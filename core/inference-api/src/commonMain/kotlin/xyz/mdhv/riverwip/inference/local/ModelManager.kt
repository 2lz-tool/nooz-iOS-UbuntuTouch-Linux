package xyz.mdhv.riverwip.inference.local

import okio.FileSystem
import okio.HashingSource
import okio.Path
import okio.blackholeSink
import okio.buffer
import okio.use
import kotlin.math.roundToLong

/**
 * Local model manager utilities (brief §5: checksum verification, storage
 * budget display). The catalogue itself — which models exist, their verified
 * download URL, size — now lives in `:core:data`'s `ModelCatalogueRepository`,
 * reading the constellation's shared `ai-catalogue/models.json` (real,
 * live-probed mirrors) rather than a hardcoded, permanently-unverified pair of
 * placeholder entries. This object stays here as pure, platform-free utilities
 * both that repository and [LocalLlamaProvider] can use.
 */
object ChecksumVerifier {
    fun sha256Hex(fileSystem: FileSystem, path: Path): String =
        HashingSource.sha256(fileSystem.source(path)).use { hashing ->
            hashing.buffer().readAll(blackholeSink())
            hashing.hash.hex()
        }

    fun verify(fileSystem: FileSystem, path: Path, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return false
        return sha256Hex(fileSystem, path).equals(expectedSha256, ignoreCase = true)
    }
}

/** Storage-budget checks (brief §5: "storage budget display"). Pure arithmetic. */
object StorageBudget {
    /** Can a download of [sizeBytes] fit in [availableBytes] with [safetyMarginBytes] headroom left over? */
    fun canDownload(sizeBytes: Long, availableBytes: Long, safetyMarginBytes: Long = 500L * 1024 * 1024): Boolean =
        availableBytes - sizeBytes >= safetyMarginBytes

    fun humanReadable(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        val tenths = (value * 10).roundToLong()
        return "${tenths / 10}.${tenths % 10} ${units[unitIndex.coerceAtLeast(0)]}"
    }
}
