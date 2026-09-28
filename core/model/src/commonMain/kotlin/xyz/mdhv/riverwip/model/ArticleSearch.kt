package xyz.mdhv.riverwip.model

/**
 * Search over what a reader has actually read (owner's ask, 2026-08): "search
 * based on recall alone that they saw something or read something that they
 * remember fuzzily."
 *
 * The title-only substring match this replaces could only find a story if the
 * reader remembered words from its *headline*. Fuzzy recall almost never works
 * that way — what survives is a phrase from the middle of the piece, a name, a
 * place. So the searchable surface becomes title + summary + the extracted body
 * of every article the reader has opened.
 *
 * This object is the pure half: turning a half-remembered phrase into a safe
 * SQLite FTS `MATCH` expression, and pulling a legible excerpt out of a body so
 * the reader can see *why* something matched. Both are unit-tested without
 * Android; the storage half lives in `:core:data`.
 */
object ArticleSearch {

    /** Shortest token worth searching. One letter matches most of the corpus. */
    private const val MIN_TERM_LENGTH = 2

    /**
     * Cap on tokens sent to FTS. A pasted paragraph would otherwise become a
     * hundred ANDed prefix terms — slow, and certain to match nothing.
     */
    private const val MAX_TERMS = 8

    /**
     * Function words dropped from a multi-word query.
     *
     * Terms are ANDed, so "floods in nepal" would otherwise demand a word
     * beginning "in" — which a headline like "Flash floods on the Nepal-Tibet
     * border" does not have, and the story the reader was reaching for
     * disappears. English-only on purpose: these are the words an English
     * query actually carries, and no token here collides with a word in the
     * scripts the catalogue's other feeds are written in.
     *
     * Only applied when at least one real term survives, so searching for
     * "the" still searches for "the".
     */
    private val STOPWORDS = setOf(
        "the", "a", "an", "and", "or", "of", "in", "on", "at", "to", "for",
        "is", "are", "was", "were", "be", "by", "with", "from", "as", "that",
        "this", "it", "its",
    )

    /**
     * True for characters that belong *inside* a word.
     *
     * Not simply [Char.isLetterOrDigit]: Indic scripts write vowels and the
     * virama as combining marks, which are categorised as marks rather than
     * letters. Splitting on them tore Telugu, Devanagari, Gujarati and Odia
     * words into fragments mid-character-cluster — search was broken for
     * exactly the languages the catalogue just gained feeds in. Zero-width
     * joiner and non-joiner are word-internal in those scripts too.
     */
    private fun isWordChar(ch: Char): Boolean =
        ch.isLetterOrDigit() ||
            ch.category == CharCategory.NON_SPACING_MARK ||
            ch.category == CharCategory.COMBINING_SPACING_MARK ||
            ch.category == CharCategory.ENCLOSING_MARK ||
            ch == '‌' || ch == '‍'

    /**
     * The searchable tokens in a raw query, normalised.
     *
     * Everything outside [isWordChar] is a separator, which does three jobs at
     * once: it splits words, it strips punctuation, and — because the result is
     * reassembled rather than escaped — it makes FTS operator syntax (`"` `*`
     * `^` `:` `NEAR` `-`) structurally unable to reach the query. A reader
     * typing `"nepal" OR flood*` searches for those words, which is what they
     * meant, and cannot accidentally (or deliberately) rewrite the query.
     */
    fun terms(raw: String): List<String> {
        val all = raw.split { !isWordChar(it) }
            .asSequence()
            .map { it.lowercase() }
            .filter { it.length >= MIN_TERM_LENGTH }
            .distinct()
            .toList()
        val meaningful = all.filterNot { it in STOPWORDS }
        return (if (meaningful.isNotEmpty()) meaningful else all).take(MAX_TERMS)
    }

    /**
     * A safe FTS4 `MATCH` expression, or null when there is nothing to search
     * for — callers treat null as "no query", never as "match nothing".
     *
     * Every term gets a `*` suffix: half-remembered searches are usually half-
     * remembered *words* ("austral", "monsoo"), and prefix matching is the one
     * fuzziness FTS offers without a spelling-correction table.
     *
     * Terms are joined by a **space, not `AND`**. SQLite builds FTS4 with
     * "standard query syntax" unless compiled with SQLITE_ENABLE_FTS3_PARENTHESIS,
     * and under standard syntax whitespace already means AND while the word
     * `AND` is just another term to search for. Writing `dozen* AND lawsuit*`
     * therefore demands the article also contain the literal word "and" — which
     * most prose does, so the bug would have hidden in plain sight and only
     * eaten the occasional short article. Verified against the real thing in
     * `RiverDatabaseMigrationTest`.
     */
    fun toMatchQuery(raw: String): String? {
        val terms = terms(raw)
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { "$it*" }
    }

    /**
     * A short excerpt of [body] around the first place any of [terms] appears,
     * so a result can show why it matched rather than just asserting that it
     * did. Returns null when no term appears — the caller then falls back to
     * the article's own opening, rather than showing an empty line.
     *
     * Cuts are moved outwards to whitespace so the excerpt starts and ends on
     * whole words, and ellipses mark each end that isn't the real one.
     */
    fun snippet(body: String, terms: List<String>, radius: Int = 90): String? {
        if (body.isBlank() || terms.isEmpty()) return null
        val haystack = body.lowercase()
        val hit = terms.mapNotNull { term ->
            haystack.indexOf(term).takeIf { it >= 0 }?.let { it to term }
        }.minByOrNull { it.first } ?: return null
        val (index, term) = hit

        var start = (index - radius).coerceAtLeast(0)
        var end = (index + term.length + radius).coerceAtMost(body.length)
        // Grow outwards to the nearest whitespace so we never cut mid-word.
        while (start > 0 && !body[start - 1].isWhitespace()) start--
        while (end < body.length && !body[end].isWhitespace()) end++

        val core = body.substring(start, end).trim().replace(WHITESPACE_RUN, " ")
        if (core.isEmpty()) return null
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < body.length) "…" else ""
        return "$prefix$core$suffix"
    }

    /**
     * Whether [text] contains every one of [terms] as a prefix of some word —
     * the same rule [toMatchQuery] asks FTS for.
     *
     * This exists so titles and summaries of articles the reader has *not*
     * opened stay searchable: only opened articles have an extracted body, so
     * an FTS-only search would silently answer a narrower question than the one
     * asked. Callers union the two.
     */
    fun matchesPrefixes(text: String, terms: List<String>): Boolean {
        if (terms.isEmpty()) return false
        val words = text.split { !isWordChar(it) }.map { it.lowercase() }
        return terms.all { term -> words.any { it.startsWith(term) } }
    }

    private val WHITESPACE_RUN = Regex("\\s+")

    /** Split on a character predicate, dropping empty runs. */
    private inline fun String.split(isSeparator: (Char) -> Boolean): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        for (ch in this) {
            if (isSeparator(ch)) {
                if (current.isNotEmpty()) {
                    out.add(current.toString())
                    current.setLength(0)
                }
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }
}
