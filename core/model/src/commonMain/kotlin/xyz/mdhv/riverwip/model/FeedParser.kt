package xyz.mdhv.riverwip.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * Feed parsing (brief §P2 ingest). Turns a fetched feed body into normalized
 * [ParsedItem]s. Handles RSS 2.0, Atom, RDF (RSS 1.0), and the two JSON shapes we
 * ingest (Mastodon timelines, GDELT DOC). Pure and dependency-light (JDK DOM +
 * kotlinx JSON) so the whole ingest path is unit-tested without an Android SDK.
 *
 * XML parsing is XXE-hardened (feeds are untrusted): no DTDs, no external entities.
 */
object FeedParser {

    data class ParsedItem(
        val title: String,
        val link: String,
        val author: String? = null,
        val publishedAtMillis: Long? = null,
        val summary: String? = null,
        val categories: List<String> = emptyList(),
        /** The feed's own image for this item, if any — see [rssImageUrl]/[atomImageUrl]. */
        val imageUrl: String? = null,
        /** The feed's own adult/explicit declaration for this item, if any — see [declaredNsfw]. */
        val declaredNsfw: Boolean = false,
    )

    data class ParsedFeed(val title: String?, val items: List<ParsedItem>)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parse a feed body. [contentType] disambiguates JSON vs XML when ambiguous. */
    fun parse(body: String, contentType: String? = null): ParsedFeed {
        val trimmed = body.trimStart('﻿', ' ', '\n', '\r', '\t')
        val looksJson = (contentType?.contains("json", true) == true) ||
            trimmed.startsWith("[") || trimmed.startsWith("{")
        return if (looksJson) parseJson(trimmed) else parseXml(trimmed)
    }

    // ---- XML (RSS / Atom / RDF) -----------------------------------------

    private fun parseXml(body: String): ParsedFeed {
        val doc = try {
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = true
            hardenXxe(f)
            f.newDocumentBuilder().parse(InputSource(StringReader(body)))
        } catch (_: Exception) {
            return ParsedFeed(null, emptyList())
        }
        val root = doc.documentElement ?: return ParsedFeed(null, emptyList())
        // Atom feeds have <entry>; RSS/RDF have <item>.
        val entryNodes = root.descendantsByLocal("entry")
        val itemNodes = root.descendantsByLocal("item")
        val (nodes, isAtom) = if (entryNodes.isNotEmpty() && itemNodes.isEmpty()) entryNodes to true else itemNodes to false
        val feedTitle = root.firstChildByLocal("title")?.textContent?.trim()
            ?: root.firstChildByLocal("channel")?.firstChildByLocal("title")?.textContent?.trim()
        val items = nodes.mapNotNull { if (isAtom) parseAtomEntry(it) else parseRssItem(it) }
        return ParsedFeed(feedTitle, items)
    }

    private fun parseRssItem(item: Element): ParsedItem? {
        val title = item.firstChildByLocal("title")?.textContent?.trim().orEmpty()
        val link = item.firstChildByLocal("link")?.textContent?.trim()
            ?: item.childrenByLocal("guid").firstOrNull { it.attrOrNull("isPermaLink") != "false" }?.textContent?.trim()
            ?: ""
        if (title.isBlank() && link.isBlank()) return null
        val date = item.firstChildByLocal("pubDate")?.textContent
            ?: item.firstChildByLocal("date")?.textContent // dc:date
        val author = item.firstChildByLocal("creator")?.textContent?.trim() // dc:creator
            ?: item.firstChildByLocal("author")?.textContent?.trim()
        val summaryRaw = item.firstChildByLocal("encoded")?.textContent // content:encoded
            ?: item.firstChildByLocal("description")?.textContent
        val categories = (item.childrenByLocal("category") + item.childrenByLocal("subject"))
            .mapNotNull { it.textContent?.trim()?.ifBlank { null } }
        return ParsedItem(
            title = Html.strip(title),
            link = link,
            author = author,
            publishedAtMillis = parseDate(date),
            summary = summaryRaw?.let { Html.strip(it).ifBlank { null } },
            categories = categories,
            imageUrl = rssImageUrl(item, summaryRaw),
            declaredNsfw = declaredNsfw(item),
        )
    }

    /**
     * An RSS/RDF item's image, checked in order of how explicitly it's marked
     * an image: `<enclosure type="image/...">`, Media RSS `<media:thumbnail>`
     * (always an image by definition), `<media:content medium="image">`
     * (unlike thumbnail, `<media:content>` can just as easily be video/audio,
     * so only trust it when `medium` says image explicitly), and finally the
     * first `<img>` found in the item's own description/content:encoded HTML.
     */
    private fun rssImageUrl(item: Element, rawHtml: String?): String? {
        item.childrenByLocal("enclosure")
            .firstOrNull { it.attrOrNull("type")?.startsWith("image/", ignoreCase = true) == true }
            ?.attrOrNull("url")?.trim()?.ifBlank { null }
            ?.let { return it }
        item.childrenByLocal("thumbnail").firstOrNull()
            ?.attrOrNull("url")?.trim()?.ifBlank { null }
            ?.let { return it }
        item.childrenByLocal("content")
            .firstOrNull { it.attrOrNull("medium") == "image" }
            ?.attrOrNull("url")?.trim()?.ifBlank { null }
            ?.let { return it }
        return rawHtml?.let(Html::firstImgSrc)
    }

    private fun parseAtomEntry(entry: Element): ParsedItem? {
        val title = entry.firstChildByLocal("title")?.textContent?.trim().orEmpty()
        val links = entry.childrenByLocal("link")
        val link = (links.firstOrNull { it.attrOrNull("rel") == "alternate" } ?: links.firstOrNull { it.attrOrNull("rel") == null } ?: links.firstOrNull())
            ?.attrOrNull("href")?.trim() ?: ""
        if (title.isBlank() && link.isBlank()) return null
        val date = entry.firstChildByLocal("published")?.textContent
            ?: entry.firstChildByLocal("updated")?.textContent
        val author = entry.firstChildByLocal("author")?.firstChildByLocal("name")?.textContent?.trim()
        val summaryRaw = entry.firstChildByLocal("summary")?.textContent
            ?: entry.firstChildByLocal("content")?.textContent
        val categories = entry.childrenByLocal("category").mapNotNull { it.attrOrNull("term")?.trim()?.ifBlank { null } }
        return ParsedItem(
            title = Html.strip(title),
            link = link,
            author = author,
            publishedAtMillis = parseDate(date),
            summary = summaryRaw?.let { Html.strip(it).ifBlank { null } },
            categories = categories,
            imageUrl = atomImageUrl(entry, summaryRaw),
            declaredNsfw = declaredNsfw(entry),
        )
    }

    /** An Atom entry's image: an explicit `<link rel="enclosure" type="image/...">`, or the first `<img>` in its own summary/content HTML. */
    private fun atomImageUrl(entry: Element, rawHtml: String?): String? {
        entry.childrenByLocal("link")
            .firstOrNull { it.attrOrNull("rel") == "enclosure" && it.attrOrNull("type")?.startsWith("image/", ignoreCase = true) == true }
            ?.attrOrNull("href")?.trim()?.ifBlank { null }
            ?.let { return it }
        return rawHtml?.let(Html::firstImgSrc)
    }

    /**
     * Whether the *source's own feed* declared this entry adult/explicit —
     * never this app's own judgment. Checks the two real conventions feeds
     * use for this: Media RSS's `<media:rating>` (local name "rating";
     * "adult" under the default `urn:simple` scheme, the vocabulary almost
     * every feed that uses this element at all actually uses) and the
     * podcast namespace's `<itunes:explicit>` (local name "explicit"; "true"
     * per Apple's current spec, "yes" tolerated from its older one). A feed
     * that supplies neither element reads false — absence is exactly what
     * both specs themselves define as "not flagged," never a guess.
     */
    private fun declaredNsfw(item: Element): Boolean {
        item.firstChildByLocal("rating")?.textContent?.trim()?.let {
            if (it.equals("adult", ignoreCase = true)) return true
        }
        item.firstChildByLocal("explicit")?.textContent?.trim()?.let {
            if (it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true)) return true
        }
        return false
    }

    // ---- JSON (Mastodon / GDELT) ----------------------------------------

    private fun parseJson(body: String): ParsedFeed {
        val el = try { json.parseToJsonElement(body) } catch (_: Exception) { return ParsedFeed(null, emptyList()) }
        // Mastodon: a top-level array of status objects.
        if (el is JsonArray) return ParsedFeed(null, el.mapNotNull { parseMastodonStatus(it.jsonObject) })
        // GDELT: { "articles": [ ... ] }
        val obj = el as? JsonObject ?: return ParsedFeed(null, emptyList())
        val articles = obj["articles"]?.let { if (it is JsonArray) it else null }
        if (articles != null) return ParsedFeed("GDELT", articles.mapNotNull { parseGdeltArticle(it.jsonObject) })
        return ParsedFeed(null, emptyList())
    }

    private fun str(o: JsonObject, key: String): String? =
        (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.ifBlank { null }

    private fun parseMastodonStatus(o: JsonObject): ParsedItem? {
        val content = str(o, "content")?.let { Html.strip(it) }?.ifBlank { null }
        val url = str(o, "url") ?: str(o, "uri") ?: return null
        val account = (o["account"] as? JsonObject)?.let { str(it, "acct") ?: str(it, "display_name") }
        val created = str(o, "created_at")
        val tags = (o["tags"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let { t -> str(t, "name") } } ?: emptyList()
        val title = content?.take(140) ?: url
        return ParsedItem(
            title = title,
            link = url,
            author = account,
            publishedAtMillis = parseDate(created),
            summary = content,
            categories = tags,
        )
    }

    private fun parseGdeltArticle(o: JsonObject): ParsedItem? {
        val url = str(o, "url") ?: return null
        val title = str(o, "title")?.ifBlank { null } ?: url
        val domain = str(o, "domain")
        val seen = str(o, "seendate") // e.g. 20260707T193000Z
        return ParsedItem(
            title = Html.strip(title),
            link = url,
            author = domain,
            publishedAtMillis = parseGdeltDate(seen),
            summary = null,
            categories = emptyList(),
        )
    }

    // ---- dates ----------------------------------------------------------

    fun parseDate(raw: String?): Long? {
        val s = raw?.trim()?.ifBlank { null } ?: return null
        // RFC-822/1123 (RSS): "Wed, 02 Oct 2024 13:00:00 GMT"
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        // RFC-3339 / ISO-8601 with offset (Atom): "2024-10-02T13:00:00Z"
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s).toInstant().toEpochMilli() }
        return null
    }

    private val GDELT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private fun parseGdeltDate(raw: String?): Long? {
        val s = raw?.trim()?.ifBlank { null } ?: return null
        return runCatching {
            java.time.LocalDateTime.parse(s, GDELT_FMT).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }

    // ---- DOM helpers ----------------------------------------------------

    private fun hardenXxe(f: DocumentBuilderFactory) {
        for (feat in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        )) runCatching { f.setFeature(feat.first, feat.second) }
        runCatching { f.isXIncludeAware = false }
        runCatching { f.isExpandEntityReferences = false }
    }

    private fun Element.childElements(): List<Element> {
        val out = ArrayList<Element>()
        val kids = childNodes
        for (i in 0 until kids.length) (kids.item(i) as? Element)?.let { out.add(it) }
        return out
    }

    private fun Element.childrenByLocal(local: String): List<Element> =
        childElements().filter { it.localOrName().equals(local, ignoreCase = true) }

    private fun Element.firstChildByLocal(local: String): Element? = childrenByLocal(local).firstOrNull()

    private fun Element.descendantsByLocal(local: String): List<Element> {
        val out = ArrayList<Element>()
        fun walk(n: Node) {
            val kids = n.childNodes
            for (i in 0 until kids.length) {
                val k = kids.item(i)
                if (k is Element) {
                    if (k.localOrName().equals(local, ignoreCase = true)) out.add(k)
                    walk(k)
                }
            }
        }
        walk(this)
        return out
    }

    private fun Element.localOrName(): String = localName ?: tagName.substringAfter(':')
    private fun Element.attrOrNull(name: String): String? = if (hasAttribute(name)) getAttribute(name) else null
}
