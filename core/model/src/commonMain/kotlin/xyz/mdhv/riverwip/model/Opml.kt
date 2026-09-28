package xyz.mdhv.riverwip.model

/**
 * OPML import/export (brief §P1). OPML is the portable feed-list format; the
 * user's source-set is the user's file (brief §4). Pure string handling so it is
 * unit-tested without an Android SDK.
 */
object Opml {

    data class Outline(val title: String, val xmlUrl: String, val htmlUrl: String? = null)

    private val OUTLINE_TAG = Regex("<outline\\b[^>]*/?>", RegexOption.IGNORE_CASE)

    private fun attr(tag: String, name: String): String? {
        val m = Regex(
            "\\b" + Regex.escape(name) + "\\s*=\\s*(\"([^\"]*)\"|'([^']*)')",
            RegexOption.IGNORE_CASE,
        ).find(tag) ?: return null
        val v = m.groupValues[2].ifEmpty { m.groupValues[3] }
        return unescape(v)
    }

    /** Parse an OPML document into outlines that carry an `xmlUrl` (feed) attribute. */
    fun parse(xml: String): List<Outline> {
        val out = ArrayList<Outline>()
        for (m in OUTLINE_TAG.findAll(xml)) {
            val tag = m.value
            val xmlUrl = attr(tag, "xmlUrl") ?: continue
            if (xmlUrl.isBlank()) continue
            val title = attr(tag, "title") ?: attr(tag, "text") ?: xmlUrl
            val htmlUrl = attr(tag, "htmlUrl")
            out.add(Outline(title = title, xmlUrl = xmlUrl.trim(), htmlUrl = htmlUrl?.trim()))
        }
        return out
    }

    /** Convenience: parse straight into [Source] rows (all tier USER, enabled). */
    fun parseToSources(xml: String, addedAt: Long): List<Source> =
        parse(xml).map { o ->
            Source(
                id = Ids.sourceId(SourceKind.RSS, o.xmlUrl),
                kind = SourceKind.RSS,
                url = o.xmlUrl,
                title = o.title,
                tier = Tier.USER,
                enabled = true,
                addedAt = addedAt,
            )
        }

    /** Serialize sources to an OPML 2.0 document. Only feed-like kinds are exported. */
    fun export(sources: List<Source>, ownerTitle: String = "river sources"): String {
        val body = sources.joinToString("\n") { s ->
            val t = escape(s.title)
            val x = escape(s.url)
            "    <outline text=\"$t\" title=\"$t\" type=\"rss\" xmlUrl=\"$x\"/>"
        }
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<opml version=\"2.0\">\n")
            append("  <head>\n    <title>").append(escape(ownerTitle)).append("</title>\n  </head>\n")
            append("  <body>\n")
            if (body.isNotEmpty()) append(body).append("\n")
            append("  </body>\n")
            append("</opml>\n")
        }
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
