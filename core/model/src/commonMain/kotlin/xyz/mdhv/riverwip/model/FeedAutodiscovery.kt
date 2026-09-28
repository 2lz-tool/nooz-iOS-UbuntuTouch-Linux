package xyz.mdhv.riverwip.model

import java.net.URI

/**
 * Feed autodiscovery (brief §P1: "Add-by-URL with feed autodiscovery").
 *
 * Two pure pieces:
 *  - [discoverFromHtml]: pull `<link rel="alternate" type="application/rss+xml|atom+xml">`
 *    from a page's HTML and resolve relative hrefs against the page URL.
 *  - [guessFeedPaths]: common feed locations to probe when a page declares none.
 *
 * The network fetch that feeds these lives in `:core:data`; keeping the parsing
 * pure (no jsoup) means it is unit-tested without an Android SDK.
 */
object FeedAutodiscovery {

    enum class FeedType { RSS, ATOM, UNKNOWN }

    data class DiscoveredFeed(val url: String, val title: String?, val type: FeedType)

    private val LINK_TAG = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private fun attr(tag: String, name: String): String? {
        // name="value" | name='value' | name=value
        val m = Regex(
            "\\b" + Regex.escape(name) + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
            RegexOption.IGNORE_CASE,
        ).find(tag) ?: return null
        return (m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } })
            .trim()
            .ifEmpty { null }
    }

    fun discoverFromHtml(pageUrl: String, html: String): List<DiscoveredFeed> {
        val out = LinkedHashMap<String, DiscoveredFeed>() // dedup by resolved url, preserve order
        for (match in LINK_TAG.findAll(html)) {
            val tag = match.value
            val rel = attr(tag, "rel")?.lowercase() ?: continue
            if (!rel.split(Regex("\\s+")).contains("alternate")) continue
            val type = attr(tag, "type")?.lowercase() ?: ""
            val feedType = when {
                type.contains("rss") -> FeedType.RSS
                type.contains("atom") -> FeedType.ATOM
                type.contains("xml") && rel.contains("alternate") -> FeedType.UNKNOWN
                else -> continue
            }
            val href = attr(tag, "href") ?: continue
            val resolved = resolve(pageUrl, href) ?: continue
            val title = attr(tag, "title")
            out.putIfAbsent(resolved, DiscoveredFeed(resolved, title, feedType))
        }
        return out.values.toList()
    }

    /** Common feed paths to probe when a page declares no `<link rel=alternate>`. */
    fun guessFeedPaths(siteUrl: String): List<String> {
        val root = siteRoot(siteUrl) ?: return emptyList()
        return listOf(
            "/feed", "/feed/", "/rss", "/rss.xml", "/index.xml", "/atom.xml",
            "/feed.xml", "/feeds/posts/default", "/?feed=rss2", "/rss/",
        ).map { root + it }
    }

    /** True if the string plausibly points straight at a feed (skip discovery). */
    fun looksLikeFeed(url: String): Boolean {
        val u = url.lowercase()
        return u.endsWith(".rss") || u.endsWith(".xml") || u.endsWith(".atom") ||
            u.contains("/rss") || u.contains("/feed") || u.contains("format=json") ||
            u.contains("/api/v1/timelines/")
    }

    private fun siteRoot(url: String): String? = try {
        val u = URI(if (url.contains("://")) url else "https://$url")
        val scheme = u.scheme ?: "https"
        val host = u.host ?: return null
        val portPart = if (u.port == -1) "" else ":${u.port}"
        "$scheme://$host$portPart"
    } catch (_: Exception) { null }

    private fun resolve(base: String, href: String): String? = try {
        val baseUri = URI(if (base.contains("://")) base else "https://$base")
        baseUri.resolve(href.trim()).toString()
    } catch (_: Exception) {
        if (href.startsWith("http")) href else null
    }
}
