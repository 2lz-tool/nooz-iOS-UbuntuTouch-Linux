package xyz.mdhv.riverwip.model

/**
 * Tiny HTML→text helper for feed titles/summaries and Mastodon content. Strips
 * tags and decodes the common entities. Not a full HTML parser (the reader's
 * full-text extraction uses jsoup in `:core:data`); this is just enough to turn a
 * feed's escaped/marked-up snippet into clean display text. Pure.
 */
object Html {
    private val TAG = Regex("<[^>]+>")
    private val WS = Regex("\\s+")
    private val IMG_SRC = Regex("<img\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    fun strip(s: String): String =
        unescape(TAG.replace(s, " ")).let { WS.replace(it, " ") }.trim()

    /**
     * The first `<img src="...">` found in a raw (unstripped) HTML snippet, if
     * any — the last-resort image source for a feed item whose entry carries
     * no structured `<enclosure>`/Media RSS/Atom image link, only an `<img>`
     * buried in its description/content HTML.
     */
    fun firstImgSrc(s: String): String? = IMG_SRC.find(s)?.groupValues?.get(1)?.let(::unescape)?.trim()?.ifBlank { null }

    fun unescape(s: String): String {
        if ('&' !in s) return s
        var r = s
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&rsquo;", "’")
            .replace("&lsquo;", "‘")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
        // Numeric entities &#NNN; and &#xHH;
        r = Regex("&#x([0-9a-fA-F]+);").replace(r) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        }
        r = Regex("&#([0-9]+);").replace(r) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
        return r
    }
}
