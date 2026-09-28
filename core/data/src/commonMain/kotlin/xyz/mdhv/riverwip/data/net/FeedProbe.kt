package xyz.mdhv.riverwip.data.net

import xyz.mdhv.riverwip.model.FeedAutodiscovery

/** What a probe can conclude about a URL. Extracted so the repository can be tested with a fake. */
sealed interface ProbeResult {
    /** The URL is itself a parseable feed. */
    data class Feed(val url: String, val title: String?, val itemCount: Int, val format: String) : ProbeResult
    /** An HTML page that links to one or more feeds. */
    data class Candidates(val feeds: List<FeedAutodiscovery.DiscoveredFeed>) : ProbeResult
    /** Resolved, but neither a feed nor a page declaring one. */
    data class NotAFeed(val reason: String) : ProbeResult
    data class Error(val reason: String) : ProbeResult
}

/** Abstraction over probing so [xyz.mdhv.riverwip.data.repo.SourceRepository] is unit-testable. */
interface FeedProber {
    suspend fun probe(inputUrl: String): ProbeResult
}

/**
 * Probe a user-entered URL: is it already a feed, or an HTML page that *declares*
 * feeds? This powers add-by-URL autodiscovery and the "test connection" check —
 * both legible (brief §3): we show the user exactly what resolved.
 */
class FeedProbe(private val http: HttpClient) : FeedProber {

    override suspend fun probe(inputUrl: String): ProbeResult {
        val resp = try {
            http.get(inputUrl)
        } catch (e: Exception) {
            return ProbeResult.Error(e.message ?: (e::class.simpleName ?: "Error"))
        }
        if (!resp.isSuccess) return ProbeResult.Error("HTTP ${resp.code}")
        val body = resp.body
        val format = detectFeedFormat(body, resp.contentType)
        return if (format != null) {
            ProbeResult.Feed(
                url = resp.finalUrl,
                title = extractFeedTitle(body),
                itemCount = countItems(body),
                format = format,
            )
        } else if (looksHtml(body, resp.contentType)) {
            val discovered = FeedAutodiscovery.discoverFromHtml(resp.finalUrl, body)
            if (discovered.isNotEmpty()) ProbeResult.Candidates(discovered)
            else ProbeResult.NotAFeed("HTML page declares no feeds")
        } else {
            ProbeResult.NotAFeed("unrecognized content type ${resp.contentType}")
        }
    }

    companion object {
        /** Returns "rss" | "atom" | "rdf" | "json" if the body parses as a feed, else null. */
        fun detectFeedFormat(body: String, contentType: String?): String? {
            val head = body.take(4000)
            return when {
                head.contains("<rss", ignoreCase = true) -> "rss"
                head.contains("<rdf:RDF", ignoreCase = true) && head.contains("<item", ignoreCase = true) -> "rdf"
                head.contains("<feed", ignoreCase = true) && head.contains("xmlns", ignoreCase = true) -> "atom"
                isJsonFeed(head, contentType) -> "json"
                else -> null
            }
        }

        private fun isJsonFeed(head: String, contentType: String?): Boolean {
            val ct = contentType?.lowercase() ?: ""
            if (!ct.contains("json") && !head.trimStart().startsWith("[") && !head.trimStart().startsWith("{")) return false
            // Mastodon timeline (array of statuses) or GDELT ("articles").
            return head.contains("\"content\"") || head.contains("\"articles\"") || head.contains("\"items\"")
        }

        fun looksHtml(body: String, contentType: String?): Boolean {
            val ct = contentType?.lowercase() ?: ""
            if (ct.contains("html")) return true
            val head = body.take(2000).lowercase()
            return head.contains("<!doctype html") || head.contains("<html")
        }

        fun countItems(body: String): Int {
            val items = Regex("<item[\\s>]", RegexOption.IGNORE_CASE).findAll(body).count()
            if (items > 0) return items
            return Regex("<entry[\\s>]", RegexOption.IGNORE_CASE).findAll(body).count()
        }

        fun extractFeedTitle(body: String): String? {
            // The first <title> is the channel/feed title for RSS/Atom/RDF.
            val m = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(body) ?: return null
            return m.groupValues[1]
                .replace(Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
                .trim()
                .ifEmpty { null }
        }
    }
}
