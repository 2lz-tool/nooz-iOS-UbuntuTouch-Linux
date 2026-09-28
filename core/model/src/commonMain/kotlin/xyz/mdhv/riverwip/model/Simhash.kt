package xyz.mdhv.riverwip.model

/**
 * 64-bit SimHash over a text's tokens — the second half of dedup (brief §2:
 * canonical URL + title simhash). Near-duplicate titles produce hashes a small
 * Hamming distance apart, so syndicated/rewritten copies of one story collapse
 * even when their URLs differ.
 *
 * Pure and deterministic (built on [Hashing.fnv1a64]).
 */
object Simhash {

    /**
     * Everything that is not a letter, a digit, or a **combining mark**.
     *
     * `\p{M}` is load-bearing and its omission was a silent data-loss bug. Most
     * of the world's scripts write vowels as marks rather than letters: without
     * it, "मुंबई में भारी बारिश" (heavy rain) normalised to the consonant
     * skeleton "म बई म भ र ब र श", as did "मुंबई में भारी बेरोजगारी" (heavy
     * unemployment) — two unrelated Mumbai stories landing 6 bits apart, inside
     * [NEAR_DUP_THRESHOLD], so `Dedup` kept one and discarded the other. An Urdu
     * pair landed at exactly 8.
     *
     * Nothing surfaced it: `Dedup.deduplicate` keeps a cluster's representative
     * and drops the rest with no log, error or counter, so a Hindi build merely
     * showed fewer stories than its sources sent — indistinguishable from a
     * quiet feed, in an app whose entire claim is measuring what flowed. The
     * Latin calibration below is unaffected either way, which is exactly why it
     * read as sound.
     */
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}\\p{M}]+")

    /** Shingle width (characters). Char n-grams are robust for short titles: a
     *  small edit changes only the few shingles that span it, not whole tokens. */
    private const val SHINGLE = 4

    /**
     * Normalize: NFC-compose, lowercase, collapse runs of everything else to a
     * single space.
     *
     * The NFC pass matters because feeds are not consistent about composed
     * versus decomposed forms — Devanagari nukta letters have both spellings
     * (U+0929 versus U+0928 U+093C), and without composing first, one
     * publisher's encoding of a headline would not dedup against another's.
     */
    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
            .lowercase()
            .replace(NON_ALNUM, " ")
            .trim()

    /** Character n-gram shingles over the normalized text. */
    fun shingles(text: String, n: Int = SHINGLE): List<String> {
        val s = normalize(text)
        if (s.isEmpty()) return emptyList()
        if (s.length <= n) return listOf(s)
        return (0..s.length - n).map { s.substring(it, it + n) }
    }

    /** Compute the 64-bit SimHash of [text]. Empty/near-empty text hashes to 0. */
    fun of(text: String): Long {
        val grams = shingles(text)
        if (grams.isEmpty()) return 0L
        val v = IntArray(64)
        // Weight by shingle frequency.
        val freq = HashMap<String, Int>()
        for (g in grams) freq[g] = (freq[g] ?: 0) + 1
        for ((g, w) in freq) {
            val h = Hashing.fnv1a64(g)
            for (bit in 0 until 64) {
                if ((h ushr bit) and 1L == 1L) v[bit] += w else v[bit] -= w
            }
        }
        var result = 0L
        for (bit in 0 until 64) {
            if (v[bit] > 0) result = result or (1L shl bit)
        }
        return result
    }

    /** Hamming distance between two simhashes (0 = identical, 64 = opposite). */
    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Default near-duplicate threshold (bits). Calibrated on char-4-gram
     * signatures: identical titles = 0, a source-suffix variant ("… - Reuters")
     * ≈ 7, unrelated headlines ≈ 30. A threshold of 8 catches syndication and
     * trivial headline edits while staying far from distinct stories —
     * deliberately conservative, since wrongly collapsing two stories would
     * distort the very omission the app measures.
     */
    const val NEAR_DUP_THRESHOLD = 8

    /**
     * The floor the length-aware threshold may not drop below, so that identical
     * and trivially-punctuated titles still collapse however short they are.
     */
    private const val MIN_THRESHOLD = 2

    /** Shingles per bit of allowed difference — see [thresholdFor]. */
    private const val SHINGLES_PER_BIT = 6

    /**
     * The near-duplicate threshold appropriate to *these two titles*, never
     * above [ceiling].
     *
     * A fixed 8 bits assumes every title carries the same amount of evidence,
     * and short ones do not: in a four-word headline one changed word is a
     * quarter of the text, and simhash over ~16 shingles is noisy enough that
     * two genuinely different stories can land inside 8 bits. Measured, with
     * marks preserved: the Urdu pair "کراچی میں شدید بارش" (heavy rain in
     * Karachi) and "کراچی میں شدید گرمی" (severe heat in Karachi) sits at
     * exactly 8 — deleted — while the *same* one-word change in Latin
     * ("Heavy rain in Karachi" / "Heavy heat in Karachi") sits at 19 and
     * survives. Scripts that pack more meaning into fewer characters are
     * systematically more exposed, which is why this surfaced in Urdu.
     *
     * So: demand proportionally closer agreement when there is less to go on.
     * A 56-character syndication pair keeps the full 8 bits and still collapses;
     * a four-word headline gets 2, which still collapses identical and
     * punctuation-only variants and no longer collapses different stories.
     */
    fun thresholdFor(textA: String, textB: String, ceiling: Int = NEAR_DUP_THRESHOLD): Int {
        val evidence = minOf(shingles(textA).size, shingles(textB).size)
        return minOf(ceiling, maxOf(MIN_THRESHOLD, evidence / SHINGLES_PER_BIT))
    }

    fun isNearDuplicate(a: Long, b: Long, threshold: Int = NEAR_DUP_THRESHOLD): Boolean =
        a != 0L && b != 0L && distance(a, b) <= threshold
}
