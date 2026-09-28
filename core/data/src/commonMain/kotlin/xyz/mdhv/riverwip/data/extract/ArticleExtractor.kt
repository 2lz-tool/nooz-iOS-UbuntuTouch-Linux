package xyz.mdhv.riverwip.data.extract

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

/**
 * Lightweight Readability-style full-text extraction (brief §P3: used when feeds
 * truncate). Pure jsoup DOM heuristics — no network, no Android dependency, so
 * it is unit-tested against real article HTML on the JVM.
 *
 * Algorithm:
 *  1. Strip boilerplate tags (script/style/nav/header/footer/aside/etc) and
 *     interactive chrome ([UI_CONTROL_TAGS]), then reject any candidate whose
 *     text is really markup a share/embed widget printed for the reader to
 *     copy (see [EMBEDDED_MARKUP]).
 *  2. Prefer a well-known semantic content container (schema.org's
 *     `itemprop=articleBody`, common CMS classes like `.entry-content` /
 *     `.post-content`, or a bare `<article>`/`<main>`) when it holds real
 *     copy — most CMSes and news orgs declare one, and trusting it sidesteps
 *     the density heuristic below (and its false negatives) entirely, no
 *     matter how deeply each paragraph is individually wrapped inside it.
 *  3. Otherwise, score `<p>`/`<blockquote>`/`<li>` elements as "good"
 *     (substantial text, low link-density — i.e. not a nav/share-bar full of
 *     links), then pick the container holding the most good elements —
 *     crediting both the direct parent and (at half weight) the grandparent.
 *     Many templates wrap *each* paragraph in its own one-off `<div>` (a
 *     per-block component); crediting only the direct parent would then
 *     isolate every paragraph into its own single-item "container" and the
 *     extraction would collapse to just one paragraph.
 *  4. If nothing in (3) qualifies, fall back to bare leaf `<div>`s with
 *     substantial direct text — some templates markup body copy without any
 *     semantic tag at all.
 *
 * List items are kept (prefixed with a bullet) rather than silently dropped —
 * dropping them left how-to/explainer articles gutted down to their intro.
 */
object ArticleExtractor {

    data class Extracted(
        val title: String?,
        val byline: String?,
        val paragraphs: List<String>,
    ) {
        val isUsable: Boolean get() = paragraphs.isNotEmpty()
    }

    private const val MIN_PARAGRAPH_LEN = 40
    private const val MAX_LINK_DENSITY = 0.3

    /**
     * Interactive chrome, never article prose. Stripped alongside the usual
     * boilerplate so a widget's own labels ("Embed", "Download", "Subscribe")
     * can't be read as body copy — either on their own or by leaking into the
     * text of whatever container happens to hold them.
     */
    private const val UI_CONTROL_TAGS = "button, input, select, textarea, label"

    /**
     * Markup that arrived as visible *text* rather than as elements — a
     * share/embed widget printing its own snippet for the reader to copy.
     * NPR's audio tool is the case that surfaced this: it renders
     * `&lt;iframe src="..."&gt;` inside a `<code>` block, so by the time jsoup
     * has decoded the entities it is ordinary text, and stripping `<iframe>`
     * *elements* (which this extractor already did) never touched it. The
     * whole `<li>` then scored as a perfectly good paragraph: hundreds of
     * characters, no links, and it led the article.
     *
     * Matches a deliberately narrow set of real tag names. `b`/`i`/`em`/`u`
     * are excluded on purpose so ordinary prose comparisons ("the ratio a < b
     * and c > d held") can't be mistaken for markup.
     */
    private val EMBEDDED_MARKUP = Regex(
        "<\\s*/?\\s*(iframe|script|embed|object|video|audio|img|source|div|span|table|form|" +
            "input|textarea|button|a|p|br|hr|blockquote|code|pre|style|link|meta)\\b[^<>]*>",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Common containers for the real article body across major CMSes and
     * news orgs' own schema.org markup, most-specific first. Checked before
     * falling back to whole-document density scoring.
     */
    private val CONTENT_SELECTORS = listOf(
        "[itemprop=articleBody]",
        ".article-body", ".article__body", ".articleBody", ".article-content",
        ".post-content", ".entry-content",
        ".story-body", ".story-content",
        "article",
        "main",
    )

    private val BLOCK_TAGS = setOf(
        "p", "div", "ul", "ol", "li", "table", "blockquote", "section",
        "article", "header", "footer", "nav", "figure", "form", "aside",
        "h1", "h2", "h3", "h4", "h5", "h6",
    )

    fun extract(html: String, baseUri: String = ""): Extracted {
        val doc = try {
            Ksoup.parse(html, baseUri)
        } catch (_: Exception) {
            return Extracted(null, null, emptyList())
        }
        doc.select("script, style, noscript, iframe, form, svg, nav, header, footer, aside").remove()
        doc.select(UI_CONTROL_TAGS).remove()

        val title = doc.selectFirst("h1")?.text()?.ifBlank { null } ?: doc.title().ifBlank { null }
        val byline = doc.selectFirst("[rel=author], .byline, .author, meta[name=author]")
            ?.let { if (it.tagName() == "meta") it.attr("content") else it.text() }
            ?.ifBlank { null }

        // Trust an explicit semantic content container when it holds real
        // copy — sidesteps the density heuristic below (and its false
        // negatives on unusual layouts) entirely, no matter how the real
        // body is nested inside it.
        val namedScope = CONTENT_SELECTORS.asSequence()
            .mapNotNull { doc.selectFirst(it) }
            .firstOrNull { root -> textCandidates(root).any(::isGoodParagraph) }

        if (namedScope != null) {
            val paragraphs = textCandidates(namedScope)
                .filter(::isGoodParagraph)
                .map(::elementText)
                .filter { it.isNotBlank() }
            return Extracted(title, byline, paragraphs)
        }

        val goodElements = textCandidates(doc).filter(::isGoodParagraph).ifEmpty {
            // Some templates markup body copy as bare `<div>`s with no
            // semantic tag at all — pick up leaf divs with substantial text.
            doc.select("div").filter { isLeafBlock(it) && isGoodParagraph(it) }
        }
        if (goodElements.isEmpty()) return Extracted(title, byline, emptyList())

        // The container holding the most good elements is the article body —
        // boilerplate paragraphs (nav links, footers) tend to be isolated or
        // share a container with few other substantial paragraphs. Both the
        // direct parent and (at half weight) the grandparent are credited:
        // many templates wrap each paragraph in its own one-off `<div>`, and
        // crediting only the direct parent would isolate every paragraph
        // into its own single-item "container".
        val scores = HashMap<Element, Double>()
        for (el in goodElements) {
            val parent = el.parent() ?: continue
            scores[parent] = (scores[parent] ?: 0.0) + 1.0
            parent.parent()?.let { grandparent ->
                scores[grandparent] = (scores[grandparent] ?: 0.0) + 0.5
            }
        }
        val bestContainer = scores.maxByOrNull { it.value }?.key

        val finalParagraphs = (
            if (bestContainer != null) {
                goodElements.filter { it.parents().contains(bestContainer) }
            } else {
                goodElements
            }
        )
            .map(::elementText)
            .filter { it.isNotBlank() }

        return Extracted(title, byline, finalParagraphs)
    }

    /**
     * `<p>`/`<blockquote>`/`<li>` candidates within [scope] (searched at any
     * depth). A `<li>` that itself wraps a `<p>` is skipped — its text is
     * already counted via the inner `<p>` — so nothing is double-counted.
     */
    private fun textCandidates(scope: Element): List<Element> =
        scope.select("p, blockquote, li").filterNot { el ->
            el.tagName().equals("li", ignoreCase = true) && el.select("p").isNotEmpty()
        }

    /** Plain text for most elements; `<li>` gets a leading bullet so list content still reads as a list. */
    private fun elementText(el: Element): String {
        val text = el.text().trim()
        return if (el.tagName().equals("li", ignoreCase = true)) "•  $text" else text
    }

    private fun isGoodParagraph(p: Element): Boolean {
        val text = p.text().trim()
        if (text.length < MIN_PARAGRAPH_LEN) return false
        // A block that prints markup at the reader is a widget, not writing —
        // drop the whole candidate rather than trying to clean the tag out of
        // it, since what's left is invariably the widget's own labels.
        if (EMBEDDED_MARKUP.containsMatchIn(text)) return false
        return linkDensity(p) < MAX_LINK_DENSITY
    }

    /** True if [el] has no block-level element children — i.e. it's a leaf that plausibly holds its own text, not a wrapper. */
    private fun isLeafBlock(el: Element): Boolean =
        el.children().none { it.tagName().lowercase() in BLOCK_TAGS }

    private fun linkDensity(el: Element): Double {
        val textLen = el.text().length
        if (textLen == 0) return 1.0
        val linkTextLen = el.select("a").sumOf { it.text().length }
        return linkTextLen.toDouble() / textLen
    }
}
