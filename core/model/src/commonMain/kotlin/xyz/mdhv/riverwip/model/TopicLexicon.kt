package xyz.mdhv.riverwip.model

/**
 * The classification lexicon (brief §2b): keyword/phrase rules per topic, plus a
 * feed-category → topic mapping (§2a). These are the *rules*, not RESERVED
 * display labels — every match is recorded as [TopicEvidence] so a tap can reveal
 * exactly which term fired (brief §3 total inspectability).
 *
 * v1 is deliberately a transparent lexicon, not an opaque classifier; an
 * on-device embedding upgrade is a logged open question (STATE.md §10).
 */
object TopicLexicon {

    /** Keyword/phrase terms per topic. Matched on word boundaries, case-insensitive. */
    val terms: Map<Topic, List<String>> = mapOf(
        Topic.POLITICS to listOf(
            "election", "parliament", "congress", "senate", "president", "prime minister",
            "government", "policy", "minister", "lawmaker", "legislation", "vote", "voter",
            "campaign", "democrat", "republican", "bjp", "coalition", "referendum", "cabinet",
            "diplomacy", "sanction", "supreme court", "governor", "impeachment", "ballot",
        ),
        Topic.CONFLICT to listOf(
            "war", "airstrike", "missile", "troops", "ceasefire", "militant", "insurgent",
            "gunfire", "shelling", "offensive", "casualties", "hostage", "terror", "militia",
            "occupation", "invasion", "border clash", "rebel", "drone strike", "genocide",
            "armed forces", "front line",
        ),
        Topic.BUSINESS to listOf(
            "market", "stocks", "shares", "economy", "inflation", "interest rate", "gdp",
            "revenue", "profit", "earnings", "merger", "acquisition", "ipo", "startup",
            "trade", "tariff", "central bank", "recession", "unemployment", "currency",
            "investor", "nasdaq", "sensex", "rupee", "quarterly",
        ),
        Topic.TECH to listOf(
            "software", "app", "smartphone", "chip", "semiconductor", "artificial intelligence",
            "machine learning", "algorithm", "startup", "cybersecurity", "data breach", "cloud",
            "gadget", "processor", "open source", "encryption", "social media", "silicon",
            "robot", "quantum computing",
        ),
        Topic.SCIENCE to listOf(
            "research", "study", "scientist", "physics", "astronomy", "space", "nasa", "isro",
            "galaxy", "particle", "genome", "biology", "chemistry", "experiment", "telescope",
            "satellite", "spacecraft", "fossil", "evolution", "laboratory",
        ),
        Topic.CLIMATE to listOf(
            "climate", "climate change", "global warming", "emissions", "carbon", "renewable",
            "solar", "wind power", "drought", "flood", "wildfire", "heatwave", "biodiversity",
            "deforestation", "greenhouse", "monsoon", "pollution", "net zero", "glacier",
            "cyclone",
        ),
        Topic.HEALTH to listOf(
            "health", "hospital", "disease", "virus", "vaccine", "outbreak", "pandemic",
            "cancer", "mental health", "medicine", "clinical", "who", "epidemic", "infection",
            "diabetes", "surgery", "drug", "patients", "public health",
        ),
        Topic.CULTURE to listOf(
            "film", "movie", "music", "album", "festival", "art", "artist", "book", "author",
            "theatre", "celebrity", "actor", "director", "museum", "fashion", "cinema",
            "streaming", "box office", "concert", "award",
        ),
        Topic.SPORT to listOf(
            "match", "tournament", "cricket", "football", "soccer", "goal", "world cup",
            "olympics", "medal", "championship", "league", "player", "coach", "wicket",
            "innings", "striker", "tennis", "grand slam", "ipl", "fifa",
        ),
    )

    /** Feed-declared categories mapped to the taxonomy (case-insensitive, exact/substring). */
    val categoryMap: Map<String, Topic> = mapOf(
        "politics" to Topic.POLITICS, "world" to Topic.POLITICS, "nation" to Topic.POLITICS,
        "national" to Topic.POLITICS, "election" to Topic.POLITICS, "government" to Topic.POLITICS,
        "war" to Topic.CONFLICT, "conflict" to Topic.CONFLICT, "military" to Topic.CONFLICT,
        "defence" to Topic.CONFLICT, "defense" to Topic.CONFLICT,
        "business" to Topic.BUSINESS, "economy" to Topic.BUSINESS, "markets" to Topic.BUSINESS,
        "finance" to Topic.BUSINESS, "money" to Topic.BUSINESS,
        "technology" to Topic.TECH, "tech" to Topic.TECH, "gadgets" to Topic.TECH,
        "science" to Topic.SCIENCE, "space" to Topic.SCIENCE,
        "climate" to Topic.CLIMATE, "environment" to Topic.CLIMATE, "weather" to Topic.CLIMATE,
        "health" to Topic.HEALTH, "wellness" to Topic.HEALTH, "medicine" to Topic.HEALTH,
        "culture" to Topic.CULTURE, "entertainment" to Topic.CULTURE, "arts" to Topic.CULTURE,
        "lifestyle" to Topic.CULTURE, "film" to Topic.CULTURE, "music" to Topic.CULTURE,
        "sport" to Topic.SPORT, "sports" to Topic.SPORT, "cricket" to Topic.SPORT,
        "football" to Topic.SPORT,
    )

    /**
     * Terms in the languages the catalogue actually publishes in, merged onto
     * [terms]. Generated from the files in `i18n/lexicon/` — the same ones the web
     * reader's classifier is built from, so the two cannot drift into
     * classifying the same story differently.
     */
    val allTerms: Map<Topic, List<String>> by lazy {
        val merged = LinkedHashMap<Topic, MutableList<String>>()
        for ((topic, list) in terms) merged[topic] = list.toMutableList()
        for ((key, list) in TopicLexiconL10n.TERMS) {
            // fromKey falls back to OTHER rather than returning null, so an
            // unrecognised key would quietly pile every term it carries into
            // OTHER and classify news as "other" with confidence. Skip instead.
            val topic = Topic.entries.firstOrNull { it.key == key } ?: continue
            merged.getOrPut(topic) { mutableListOf() }.addAll(list)
        }
        merged
    }

    /** Precompiled word-boundary matchers per term, per topic. */
    val matchers: Map<Topic, List<Pair<String, Regex>>> by lazy {
        allTerms.mapValues { (_, list) -> list.map { term -> term to matcherFor(term) } }
    }

    /**
     * A matcher for one keyword that can actually fire in the script the
     * keyword is written in.
     *
     * This used to be `Regex("\\b" + escape(term) + "\\b")`. Java defines
     * `\b` against `\w`, which is `[a-zA-Z_0-9]` and nothing else unless
     * `UNICODE_CHARACTER_CLASS` is set — so a Devanagari, Bengali, Tamil or
     * Arabic term could **never** match: there is no word/non-word transition
     * at the edge of a character the engine does not consider a word character
     * at all. Every non-English term added to this lexicon would have been
     * silently inert, and the failure would have looked exactly like "the
     * keyword just isn't in this headline".
     *
     * `\p{M}` is in the character class alongside `\p{L}` and `\p{N}`
     * deliberately: Indic combining marks (matras, viramas, nuktas) are `M`,
     * not `L`, so without it "चुनाव" inside "चुनावों" would be treated as a
     * whole word and match, and worse, a term ending before a matra would look
     * like a boundary. The same fix, for the same reason, as the one in
     * ArticleSearch and Simhash.
     */
    internal fun matcherFor(term: String): Regex =
        if (hasUnspacedScript(term)) {
            Regex(Regex.escape(term), RegexOption.IGNORE_CASE)
        } else {
            Regex("(?<!$WORD_CHAR)" + Regex.escape(term) + "(?!$WORD_CHAR)", RegexOption.IGNORE_CASE)
        }

    // \p{N} spelled out as Nd|Nl|No: Kotlin/Native's regex engine has no one-letter N class.
    private const val WORD_CHAR = "[\\p{L}\\p{Nd}\\p{Nl}\\p{No}\\p{M}]"

    /**
     * Scripts written without spaces between words. A "word boundary" is not a
     * meaningful idea inside a run of Han or Thai, so a term in one of these is
     * matched by containment instead — the boundary form would demand a
     * non-letter on each side and could never fire mid-sentence.
     */
    private fun hasUnspacedScript(term: String): Boolean {
        var i = 0
        while (i < term.length) {
            val c = term[i]
            var cp = c.code
            if (c.isHighSurrogate() && i + 1 < term.length && term[i + 1].isLowSurrogate()) {
                cp = 0x10000 + ((c.code - 0xD800) shl 10) + (term[i + 1].code - 0xDC00)
                i++
            }
            i++
            if (isUnspacedScript(cp)) return true
        }
        return false
    }

    /**
     * Han, Hiragana, Katakana, Thai, Lao and Khmer by code-point range. This used to be
     * `\p{IsHan}\p{IsHiragana}…`, which Kotlin/Native's regex engine cannot compile; explicit
     * ranges behave the same on every platform (and cover supplementary-plane Han).
     */
    private fun isUnspacedScript(cp: Int): Boolean = when (cp) {
        in 0x2E80..0x2FDF, 0x3005, 0x3007, in 0x3021..0x3029, in 0x3038..0x303B,
        in 0x3400..0x4DBF, in 0x4E00..0x9FFF, in 0xF900..0xFAFF, in 0x20000..0x323AF -> true // Han
        in 0x3041..0x3096, in 0x309D..0x309F, in 0x1B001..0x1B11F, 0x1F200 -> true // Hiragana
        in 0x30A1..0x30FA, in 0x30FD..0x30FF, in 0x31F0..0x31FF, in 0x32D0..0x32FE,
        in 0x3300..0x3357, in 0xFF66..0xFF6F, in 0xFF71..0xFF9D -> true // Katakana
        in 0x0E01..0x0E3A, in 0x0E40..0x0E5B -> true // Thai
        in 0x0E81..0x0EDF -> true // Lao
        in 0x1780..0x17FF, in 0x19E0..0x19FF -> true // Khmer
        else -> false
    }
}
