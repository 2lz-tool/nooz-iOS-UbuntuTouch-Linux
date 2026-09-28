package xyz.mdhv.riverwip.model

/** Small helpers for things `java.lang`/`java.util` offered that Kotlin/Native's common stdlib does not. */
internal object CodePoints {
    /** The UTF-16 string for [cp], or null when it is not a Unicode scalar value (out of range, or a lone surrogate). */
    fun toStringOrNull(cp: Int): String? = when {
        cp < 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF -> null
        cp < 0x10000 -> cp.toChar().toString()
        else -> {
            val v = cp - 0x10000
            charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
    }
}

/** `map.merge(key, by, Int::plus)`, which is `java.util.Map`-only. */
internal fun <K> MutableMap<K, Int>.addCount(key: K, by: Int = 1) {
    this[key] = (this[key] ?: 0) + by
}
