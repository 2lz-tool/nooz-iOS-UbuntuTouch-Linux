package xyz.mdhv.riverwip.model

import java.net.URI

/**
 * Canonical URL normalization — the first half of dedup (brief §2: canonical URL
 * + title simhash). Pure and deterministic. The goal is that two links to the
 * same article collapse to one string, without over-merging distinct articles.
 *
 * Rules (conservative):
 *  - add `https://` if scheme missing; lowercase scheme + host; drop default port
 *  - drop the fragment
 *  - strip known tracking params (utm_*, fbclid, gclid, …), keep the rest, sorted
 *  - drop a trailing slash on non-root paths
 * We deliberately do NOT strip `www.`, reorder path segments, or lowercase the
 * path — those risk merging genuinely different resources.
 */
object CanonicalUrl {

    private val TRACKING_EXACT = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "mc_cid", "mc_eid",
        "igshid", "ref", "ref_src", "referrer", "cmpid", "cid", "spm", "_hsenc", "_hsmi",
        "vero_id", "oly_enc_id", "oly_anon_id", "s_cid", "sr_share", "guccounter",
    )
    private val TRACKING_PREFIX = listOf("utm_", "utm-", "pk_", "piwik_", "matomo_")

    fun canonicalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return try {
            val uri = URI(withScheme)
            val scheme = (uri.scheme ?: "https").lowercase()
            val host = uri.host?.lowercase() ?: return stripFragment(withScheme)
            val port = uri.port
            val defaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
            val portPart = if (port == -1 || defaultPort) "" else ":$port"

            var path = uri.rawPath ?: ""
            if (path.length > 1 && path.endsWith("/")) path = path.dropLast(1)

            val query = normalizeQuery(uri.rawQuery)
            buildString {
                append(scheme).append("://").append(host).append(portPart).append(path)
                if (query.isNotEmpty()) append("?").append(query)
            }
        } catch (_: Exception) {
            // Malformed input: at least drop the fragment so identical links match.
            stripFragment(withScheme)
        }
    }

    private fun stripFragment(s: String): String {
        val i = s.indexOf('#')
        return if (i >= 0) s.substring(0, i) else s
    }

    private fun isTracking(key: String): Boolean {
        val k = key.lowercase()
        if (k in TRACKING_EXACT) return true
        return TRACKING_PREFIX.any { k.startsWith(it) }
    }

    private fun normalizeQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrEmpty()) return ""
        val pairs = rawQuery.split("&")
            .filter { it.isNotEmpty() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                val key = if (eq >= 0) part.substring(0, eq) else part
                if (isTracking(key)) null else part
            }
            .sorted()
        return pairs.joinToString("&")
    }
}
