package xyz.mdhv.riverwip.model

/**
 * The languages Nooz offers its own interface in.
 *
 * The owner's brief on this is unusually direct, and it sets the standard the
 * rest of this file is trying to meet: *"The purpose of this free app which
 * shows consumption is public welfare, nothing else; and to that end we must
 * make it as accessible as possible, nothing less than that is acceptable."*
 *
 * Two things follow, and the second is the one that shapes the code.
 *
 * **A language is data, not a code change.** The earlier framing — that each
 * new language taxes every future copy change — was rejected outright, and
 * correctly. Adding a locale here is: one row in this list, one
 * `values-<tag>/strings.xml`, one JSON file for the web. No Kotlin, no
 * JavaScript, no build change. Removing one is deleting the same three things.
 * Anything that makes adding the sixteenth language harder than the second is a
 * bug in this design, not a cost of translation.
 *
 * **Partial is allowed, and safe.** Android resolves each string individually
 * and falls back per-key to `values/`; the web layer does the same. A locale
 * with a third of its strings translated shows that third in the reader's
 * language and the rest in English — never a blank, never a key name. That is
 * what makes it possible to *start* thirty languages rather than finish two,
 * and it is why completeness is reported rather than enforced.
 *
 * **What this list is not.** It is not a claim that thirty translations are
 * finished or native-reviewed. `Locale.completeness` is measured from the
 * resources actually present, so the app and CI can both tell the truth about
 * how far along each one is.
 */
object Locales {

    /**
     * Writing direction. Only the two that exist in this list; a language is
     * not "RTL" so much as its script is, which is why this hangs off the
     * script rather than the language.
     */
    enum class Direction { LTR, RTL }

    /**
     * @param tag        BCP 47, exactly as Android's `values-b+xx` and the web's
     *                   `lang` attribute want it.
     * @param endonym    the language's name **in that language**, which is the
     *                   only name that helps someone who cannot read the list's
     *                   other entries. A picker written entirely in English is
     *                   a picker for people who already have English.
     * @param englishName the name in English, for logs, CI output and the
     *                   owner's own reading — never the primary label.
     * @param direction  LTR or RTL.
     * @param note       anything the reader or a future maintainer should know
     *                   that the other fields cannot carry.
     */
    data class Locale(
        val tag: String,
        val endonym: String,
        val englishName: String,
        val direction: Direction = Direction.LTR,
        val note: String? = null,
    )

    /** The source language every other locale falls back to, per key. */
    const val BASE_TAG = "en"

    /**
     * India's fifteen most-spoken languages, in descending order of speakers
     * (2011 Census of India, first language plus widely-used second).
     *
     * The order is not cosmetic: it is the order the picker offers them in, and
     * for a reader who does not read English the first screenful is most of the
     * decision.
     *
     * Two entries carry a caveat rather than being quietly dropped. Santali is
     * written in Ol Chiki (U+1C50–U+1C7F), and Kashmiri in a Perso-Arabic
     * script; both are covered by Noto faces, but those faces are not on every
     * device image the way Devanagari and Tamil are. They are listed because
     * omitting the languages of ~8.5 million people to avoid an imperfect
     * render is not a trade this app gets to make quietly — see `note`.
     */
    val INDIA: List<Locale> = listOf(
        Locale("hi", "हिन्दी", "Hindi"),
        Locale("bn", "বাংলা", "Bengali"),
        Locale("mr", "मराठी", "Marathi"),
        Locale("te", "తెలుగు", "Telugu"),
        Locale("ta", "தமிழ்", "Tamil"),
        Locale("gu", "ગુજરાતી", "Gujarati"),
        Locale("ur", "اردو", "Urdu", Direction.RTL),
        Locale("kn", "ಕನ್ನಡ", "Kannada"),
        Locale("or", "ଓଡ଼ିଆ", "Odia"),
        Locale("ml", "മലയാളം", "Malayalam"),
        Locale("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        Locale("as", "অসমীয়া", "Assamese"),
        Locale("mai", "मैथिली", "Maithili"),
        Locale(
            "sat", "ᱥᱟᱱᱛᱟᱲᱤ", "Santali",
            note = "Ol Chiki script. Noto Sans Ol Chiki covers it, but is not " +
                "present on every device image — some readers will see fallback glyphs.",
        ),
        Locale(
            "ks", "کٲشُر", "Kashmiri", Direction.RTL,
            note = "Perso-Arabic script, as used in Jammu and Kashmir. Devanagari " +
                "Kashmiri is also in use and is not offered separately here.",
        ),
    )

    /**
     * Fifteen more of the world's most-spoken languages, by total speakers
     * (Ethnologue 2024), skipping the ones already covered by [INDIA] — Hindi,
     * Bengali and Urdu would otherwise appear twice.
     *
     * English is not in this list because it is [BASE_TAG]: it is what every
     * other locale falls back to, so it is always present rather than being one
     * choice among thirty.
     */
    val WORLD: List<Locale> = listOf(
        Locale("zh-Hans", "简体中文", "Chinese (Simplified)"),
        Locale("es", "Español", "Spanish"),
        Locale("ar", "العربية", "Arabic", Direction.RTL),
        Locale("fr", "Français", "French"),
        Locale("pt", "Português", "Portuguese"),
        Locale("ru", "Русский", "Russian"),
        Locale("id", "Bahasa Indonesia", "Indonesian"),
        Locale("de", "Deutsch", "German"),
        Locale("ja", "日本語", "Japanese"),
        Locale("tr", "Türkçe", "Turkish"),
        Locale("vi", "Tiếng Việt", "Vietnamese"),
        Locale("ko", "한국어", "Korean"),
        Locale("it", "Italiano", "Italian"),
        Locale("fa", "فارسی", "Persian", Direction.RTL),
        Locale("sw", "Kiswahili", "Swahili"),
    )

    /** English first, then India, then the rest of the world. */
    val ALL: List<Locale> = listOf(
        Locale(BASE_TAG, "English", "English"),
    ) + INDIA + WORLD

    val BY_TAG: Map<String, Locale> = ALL.associateBy { it.tag }

    /** Every tag except the base — i.e. the ones needing a translation file. */
    val TRANSLATED_TAGS: List<String> = ALL.filter { it.tag != BASE_TAG }.map { it.tag }

    fun direction(tag: String): Direction = BY_TAG[tag]?.direction ?: Direction.LTR

    /**
     * Android wants `values-b+zh+Hans`, the web wants `zh-Hans`, and BCP 47
     * writes it the web's way. This converts one to the other so the tag above
     * stays the single spelling everything else is derived from.
     */
    fun androidResourceQualifier(tag: String): String = "b+" + tag.replace('-', '+')
}
