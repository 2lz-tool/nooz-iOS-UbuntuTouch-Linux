package xyz.mdhv.riverwip.model

/**
 * Small, stable, dependency-free hashing. Stability across process runs matters:
 * source/item ids and simhash shingles are derived from these, so we cannot use
 * [String.hashCode] (JVM-stable but semantically ours to own) drift concerns —
 * we pin the algorithm here.
 */
object Hashing {
    private const val FNV64_OFFSET = -3750763034362895579L // 0xcbf29ce484222325
    private const val FNV64_PRIME = 1099511628211L         // 0x100000001b3

    /** FNV-1a 64-bit over the UTF-8 bytes of [s]. Deterministic. */
    fun fnv1a64(s: String): Long {
        var hash = FNV64_OFFSET
        val bytes = s.toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xff)
            hash *= FNV64_PRIME
        }
        return hash
    }

    /** Lowercase hex of [fnv1a64], zero-padded to 16 chars — used for ids. */
    fun fnv1a64Hex(s: String): String {
        val h = fnv1a64(s)
        return h.toULong().toString(16).padStart(16, '0')
    }
}
