package xyz.mdhv.riverwip.model

/**
 * The dictionary lens's obscure-word finder (owner's Kindle-style request:
 * underline uncommon words so their meaning can be gleaned in place). Pure and
 * testable: a token is "obscure" if it is alphabetic, at least [minLength]
 * long, and its lowercase form is absent from the supplied [common] set (the
 * bundled top-30k frequency list). Mid-sentence Capitalized words are skipped —
 * they read as proper nouns, which are names, not vocabulary.
 */
object ObscureWords {

    data class WordSpan(val start: Int, val end: Int, val word: String)

    private val WORD = Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*")
    private val SENTENCE_END = setOf('.', '!', '?', ':', '\n')

    fun detect(text: String, common: Set<String>, minLength: Int = 7): List<WordSpan> {
        if (common.isEmpty()) return emptyList()
        val out = ArrayList<WordSpan>()
        for (m in WORD.findAll(text)) {
            val w = m.value
            if (w.length < minLength) continue
            if (w.lowercase() in common) continue
            if (w[0].isUpperCase() && !isSentenceStart(text, m.range.first)) continue
            out.add(WordSpan(m.range.first, m.range.last + 1, w))
        }
        return out
    }

    /** True if the token at [index] begins the text or the first word of a sentence. */
    private fun isSentenceStart(text: String, index: Int): Boolean {
        var i = index - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0) return true
        return text[i] in SENTENCE_END
    }
}
