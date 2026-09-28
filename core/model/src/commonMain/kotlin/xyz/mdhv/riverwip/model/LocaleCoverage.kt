package xyz.mdhv.riverwip.model

/**
 * GENERATED FILE — do not edit. Generator: tools/i18n/generate.py
 *
 * How many of the interface's strings each locale has, counted from the
 * catalogues in i18n/strings/. The language picker shows this so nobody
 * chooses a language and is then surprised by a half-English screen.
 */
object LocaleCoverage {
    /** Strings in the base catalogue — the denominator. */
    const val TOTAL = 330

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
    "ar" to 330,
    "as" to 330,
    "bn" to 330,
    "de" to 330,
    "en" to 330,
    "es" to 330,
    "fa" to 330,
    "fr" to 330,
    "gu" to 330,
    "hi" to 330,
    "id" to 330,
    "it" to 330,
    "ja" to 330,
    "kn" to 330,
    "ko" to 330,
    "ks" to 119,
    "mai" to 330,
    "ml" to 330,
    "mr" to 330,
    "or" to 330,
    "pa" to 330,
    "pt" to 330,
    "ru" to 330,
    "sw" to 330,
    "ta" to 330,
    "te" to 330,
    "tr" to 330,
    "ur" to 330,
    "vi" to 330,
    "zh-Hans" to 330,
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues { it > 0 }.keys
}
