package xyz.mdhv.riverwip.model

/**
 * Biased-language lexicon (brief §P5), in the Recasens et al. (2013) lineage:
 * loaded verbs, intensifiers, emotive adjectives, and editorializing hedges.
 * These are the *rules* the lens underlines; each detected span carries its
 * evidence so a tap reveals exactly why it was flagged (brief §3).
 */
object BiasLexicon {

    enum class Category(val label: String) {
        LOADED_VERB("loaded verb"),
        INTENSIFIER("intensifier"),
        EMOTIVE_ADJECTIVE("emotive adjective"),
        EDITORIALIZING_HEDGE("editorializing hedge"),
        /** A reader's own added word (Advanced settings), never part of the shipped defaults below. */
        CUSTOM("custom"),
    }

    val terms: Map<Category, List<String>> = mapOf(
        Category.LOADED_VERB to listOf(
            // Attribution verbs that colour the quote…
            "slammed", "blasted", "bashed", "gushed", "boasted", "bragged", "touted",
            "admitted", "conceded", "claimed", "refused", "lashed out", "hit out",
            "ripped", "railed", "crowed", "vowed", "insisted", "unleashed", "erupted",
            "denied", "dismissed", "mocked", "scoffed", "fumed", "raged", "snapped",
            "warned", "threatened", "demanded", "hailed", "praised", "condemned",
            "denounced", "decried", "lambasted", "savaged", "grilled", "pounced",
            "trumpeted", "peddled", "spewed", "parroted", "chided", "berated",
            "scolded", "hyped", "downplayed", "brushed off", "shrugged off",
            // …and loaded action framings.
            "doubled down", "backpedaled", "walked back", "caved", "scrambled",
            "stormed", "seized on", "clung to", "sowed", "stoked", "fueled", "fuelled",
        ),
        Category.INTENSIFIER to listOf(
            "very", "extremely", "incredibly", "utterly", "totally", "hugely",
            "massively", "wildly", "absolutely", "completely", "remarkably",
            "highly", "deeply", "vastly", "profoundly", "enormously", "immensely",
            "tremendously", "exceedingly", "overwhelmingly", "startlingly",
            "shockingly", "unbelievably", "outrageously", "downright", "flat-out",
            "sheer", "wholly", "entirely", "thoroughly", "positively",
        ),
        Category.EMOTIVE_ADJECTIVE to listOf(
            "shocking", "outrageous", "devastating", "stunning", "horrific", "brutal",
            "disastrous", "catastrophic", "explosive", "damning", "scathing",
            "bombshell", "chaotic", "dramatic", "staggering", "alarming",
            "unprecedented", "historic", "dire", "grim", "bleak", "dreadful",
            "horrifying", "terrifying", "appalling", "disgraceful", "shameful",
            "scandalous", "searing", "blistering", "withering", "fiery", "heated",
            "bitter", "furious", "controversial", "embattled", "beleaguered",
            "troubled", "botched", "bungled", "reckless", "radical", "extreme",
            "sweeping", "seismic", "landmark", "watershed", "monumental", "colossal",
            "crippling", "spiraling", "spiralling", "rampant", "raging", "toxic",
            "sinister", "grave", "stark", "damaging", "relentless", "ruthless",
        ),
        Category.EDITORIALIZING_HEDGE to listOf(
            "so-called", "allegedly", "reportedly", "apparently", "notoriously",
            "infamously", "supposedly", "purportedly", "arguably", "clearly",
            "obviously", "of course", "seemingly", "presumably", "ostensibly",
            "undoubtedly", "unsurprisingly", "predictably", "tellingly", "curiously",
            "needless to say", "make no mistake", "to be sure", "some say",
            "critics say", "critics argue", "many believe", "sources say",
            "insiders say", "it is understood", "it is believed",
        ),
    )

    /** Precompiled word-boundary matchers, longest-first so phrases win over words. */
    val matchers: List<Triple<Category, String, Regex>> by lazy {
        terms.flatMap { (cat, list) -> list.map { term -> Triple(cat, term, term) } }
            .sortedByDescending { it.third.length }
            .map { (cat, term, _) ->
                Triple(cat, term, Regex("\\b" + Regex.escape(term) + "\\b", RegexOption.IGNORE_CASE))
            }
    }
}
