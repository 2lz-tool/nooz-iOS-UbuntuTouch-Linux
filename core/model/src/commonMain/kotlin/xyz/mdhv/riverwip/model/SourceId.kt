package xyz.mdhv.riverwip.model

/**
 * Stable id derivation. Ids are content-derived so that re-adding the same feed,
 * or re-fetching the same article, is idempotent (no duplicate rows).
 */
object Ids {
    /** A source id is stable across re-adds of the same kind+url. */
    fun sourceId(kind: SourceKind, url: String): String =
        "src_${kind.key}_${Hashing.fnv1a64Hex(url.trim())}"

    /** An item id is stable across re-fetches: derived from the canonical URL. */
    fun itemId(canonicalUrl: String): String =
        "itm_${Hashing.fnv1a64Hex(canonicalUrl)}"
}
