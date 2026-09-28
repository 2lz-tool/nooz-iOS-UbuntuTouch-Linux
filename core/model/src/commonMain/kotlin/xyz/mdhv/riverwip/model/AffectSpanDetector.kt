package xyz.mdhv.riverwip.model

/**
 * Lexicon-first affect-span detection (brief §P5). Finds spans of loaded language
 * to pre-underline in the reader. Each span carries its evidence — the term and
 * why it fired — so the bottom sheet can say e.g. "flagged: 'slammed' — loaded
 * verb". Pure and deterministic.
 *
 * Detection is framed as the app's read, never a verdict (brief §7): it is an
 * opinion the user can inspect, accept, or dismiss.
 */
object AffectSpanDetector {

    data class Span(
        val start: Int,
        val end: Int,
        val text: String,
        val category: BiasLexicon.Category,
        val term: String,
    ) {
        /** Human-facing evidence line, e.g. "flagged: 'slammed', loaded verb". */
        val evidence: String get() = "flagged: '$text', ${category.label}"
    }

    /**
     * Detect non-overlapping affect spans, left to right. When matches overlap,
     * the earlier start wins; ties break toward the longer (phrase) match — so a
     * multi-word term like "so-called" is preferred over any word inside it.
     *
     * [disabledDefaultTerms] (Advanced settings: turn individual default words
     * off) is checked against the shipped [BiasLexicon] terms only — a reader
     * can silence "very" without silencing every intensifier. [customTerms]
     * (Advanced settings: a reader's own added words) are matched the same
     * way as any default term, word-boundary and case-insensitive, tagged
     * [BiasLexicon.Category.CUSTOM] so they're identifiable in the evidence
     * line. Both default empty, so an unqualified `detect(text)` call behaves
     * exactly as before this setting existed.
     */
    fun detect(
        text: String,
        disabledDefaultTerms: Set<String> = emptySet(),
        customTerms: Set<String> = emptySet(),
    ): List<Span> {
        val candidates = ArrayList<Span>()
        for ((category, term, regex) in BiasLexicon.matchers) {
            if (term in disabledDefaultTerms) continue
            for (m in regex.findAll(text)) {
                candidates.add(Span(m.range.first, m.range.last + 1, m.value, category, term))
            }
        }
        for (raw in customTerms) {
            val term = raw.trim()
            if (term.isEmpty()) continue
            val regex = Regex("\\b" + Regex.escape(term) + "\\b", RegexOption.IGNORE_CASE)
            for (m in regex.findAll(text)) {
                candidates.add(Span(m.range.first, m.range.last + 1, m.value, BiasLexicon.Category.CUSTOM, term))
            }
        }
        candidates.sortWith(compareBy({ it.start }, { -(it.end - it.start) }))
        val chosen = ArrayList<Span>()
        var lastEnd = -1
        for (s in candidates) {
            if (s.start >= lastEnd) {
                chosen.add(s)
                lastEnd = s.end
            }
        }
        return chosen
    }

    /** Count only, for the reader-chrome toggle badge. */
    fun count(text: String, disabledDefaultTerms: Set<String> = emptySet(), customTerms: Set<String> = emptySet()): Int =
        detect(text, disabledDefaultTerms, customTerms).size
}
