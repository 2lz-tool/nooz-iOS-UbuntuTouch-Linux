package xyz.mdhv.riverwip.model

/**
 * Word-level translation dictionaries the reader can download (owner's ask,
 * 2026-08: "if a user is reading this in a second or third language... they
 * might just want to be able to long press a word and see it in their native
 * language even if we don't translate the entire thing... how Kindle does it").
 *
 * Deliberately **word-level, not document-level**. Translating a whole article
 * would put a machine's paraphrase where a publisher's sentences were, which is
 * the one thing this reader does not do to a story; a glossed word leaves the
 * article intact and answers the question actually being asked.
 *
 * Data is WikDict's Wiktionary-derived SQLite exports (CC BY-SA 3.0) — the same
 * shape of arrangement as the existing [DictionaryCatalog]: fetched once, on
 * the reader's say-so, then queried entirely offline. Nothing about a lookup
 * ever leaves the device.
 *
 * **Honest gap:** there is no Indic pair here. WikDict publishes 650 pairs and
 * not one covers Hindi, Telugu, Tamil, Bengali, Malayalam, Kannada, Marathi,
 * Gujarati, Punjabi, Odia or Urdu, which is awkward given the catalogue's own
 * India expansion (D35). FreeDict has exactly one (`eng-hin`) in a different
 * format. Rather than ship a language picker that quietly omits the languages
 * this app just added feeds in, that gap is recorded here and in STATE.md.
 */
enum class TranslationFormat {
    /** WikDict's SQLite export: `simple_translation(written_rep, trans_list, …)`. */
    WIKDICT_SQLITE,
}

data class TranslationOption(
    val id: String,
    val sourceLang: String,
    val targetLang: String,
    val sourceName: String,
    val targetName: String,
    val downloadUrl: String,
    /**
     * Size measured during the 2026-09-01 verification run, for the picker to
     * show before a reader commits to a download. An estimate on purpose:
     * WikDict regenerates these, so this is never used as a correctness check.
     */
    val approxSizeBytes: Long,
    val license: String = LICENSE,
    val format: TranslationFormat = TranslationFormat.WIKDICT_SQLITE,
) {
    /** "English → Spanish". */
    val label: String get() = "$sourceName → $targetName"

    val approxSizeHuman: String
        get() {
            val mb = approxSizeBytes / (1024.0 * 1024.0)
            return if (mb < 1.0) "${(approxSizeBytes / 1024.0).toInt()} KB" else "${Math.round(mb)} MB"
        }

    companion object {
        const val LICENSE = "Wiktionary via WikDict, CC BY-SA 3.0"
    }
}

object TranslationCatalog {

    private const val BASE_URL = "https://download.wikdict.com/dictionaries/sqlite/2"

    private fun opt(
        id: String,
        source: String,
        target: String,
        sourceName: String,
        targetName: String,
        approxSizeBytes: Long,
    ) = TranslationOption(
        id = id,
        sourceLang = source,
        targetLang = target,
        sourceName = sourceName,
        targetName = targetName,
        downloadUrl = "$BASE_URL/$id.sqlite3",
        approxSizeBytes = approxSizeBytes,
    )

    /**
     * Every pair below returned HTTP 200 on 2026-09-01, fetched with the
     * User-Agent `HttpClient` actually sends rather than a browser's.
     */
    val options: List<TranslationOption> = listOf(
        opt("bg-en", "bg", "en", "Bulgarian", "English", 5136384L),
        opt("ca-en", "ca", "en", "Catalan", "English", 5189632L),
        opt("zh-en", "zh", "en", "Chinese", "English", 13086720L),
        opt("cs-en", "cs", "en", "Czech", "English", 5857280L),
        opt("da-en", "da", "en", "Danish", "English", 2756608L),
        opt("nl-en", "nl", "en", "Dutch", "English", 9998336L),
        opt("en-bg", "en", "bg", "English", "Bulgarian", 11239424L),
        opt("en-ca", "en", "ca", "English", "Catalan", 9711616L),
        opt("en-zh", "en", "zh", "English", "Chinese", 5169152L),
        opt("en-cs", "en", "cs", "English", "Czech", 9740288L),
        opt("en-da", "en", "da", "English", "Danish", 7995392L),
        opt("en-nl", "en", "nl", "English", "Dutch", 12623872L),
        opt("en-fi", "en", "fi", "English", "Finnish", 19202048L),
        opt("en-fr", "en", "fr", "English", "French", 24039424L),
        opt("en-de", "en", "de", "English", "German", 20873216L),
        opt("en-el", "en", "el", "English", "Greek", 12025856L),
        opt("en-id", "en", "id", "English", "Indonesian", 4550656L),
        opt("en-ga", "en", "ga", "English", "Irish", 4751360L),
        opt("en-it", "en", "it", "English", "Italian", 14819328L),
        opt("en-ja", "en", "ja", "English", "Japanese", 9441280L),
        opt("en-ku", "en", "ku", "English", "Kurdish", 4681728L),
        opt("en-la", "en", "la", "English", "Latin", 6021120L),
        opt("en-lt", "en", "lt", "English", "Lithuanian", 4243456L),
        opt("en-mg", "en", "mg", "English", "Malagasy", 761856L),
        opt("en-no", "en", "no", "English", "Norwegian", 5525504L),
        opt("en-pl", "en", "pl", "English", "Polish", 16003072L),
        opt("en-pt", "en", "pt", "English", "Portuguese", 12853248L),
        opt("en-ru", "en", "ru", "English", "Russian", 20938752L),
        opt("en-es", "en", "es", "English", "Spanish", 16146432L),
        opt("en-sv", "en", "sv", "English", "Swedish", 12492800L),
        opt("en-tr", "en", "tr", "English", "Turkish", 8417280L),
        opt("fi-en", "fi", "en", "Finnish", "English", 16867328L),
        opt("fr-en", "fr", "en", "French", "English", 23113728L),
        opt("de-en", "de", "en", "German", "English", 26505216L),
        opt("el-en", "el", "en", "Greek", "English", 9871360L),
        opt("id-en", "id", "en", "Indonesian", "English", 1527808L),
        opt("ga-en", "ga", "en", "Irish", "English", 2035712L),
        opt("it-en", "it", "en", "Italian", "English", 8146944L),
        opt("ja-en", "ja", "en", "Japanese", "English", 5505024L),
        opt("ku-en", "ku", "en", "Kurdish", "English", 5382144L),
        opt("la-en", "la", "en", "Latin", "English", 1937408L),
        opt("lt-en", "lt", "en", "Lithuanian", "English", 1466368L),
        opt("mg-en", "mg", "en", "Malagasy", "English", 147456L),
        opt("no-en", "no", "en", "Norwegian", "English", 1933312L),
        opt("pl-en", "pl", "en", "Polish", "English", 16723968L),
        opt("pt-en", "pt", "en", "Portuguese", "English", 6397952L),
        opt("ru-en", "ru", "en", "Russian", "English", 17776640L),
        opt("es-en", "es", "en", "Spanish", "English", 11063296L),
        opt("sv-en", "sv", "en", "Swedish", "English", 9887744L),
        opt("tr-en", "tr", "en", "Turkish", "English", 4112384L),
    )

    fun byId(id: String?): TranslationOption? = id?.let { key -> options.firstOrNull { it.id == key } }

    /** Pairs that translate *out of* [lang] — what a reader of that language's articles needs. */
    fun fromLanguage(lang: String): List<TranslationOption> = options.filter { it.sourceLang == lang }

    /** The languages a reader can translate into, for a picker grouped by destination. */
    fun targetLanguages(): List<String> = options.map { it.targetName }.distinct().sorted()
}

/** Parsing for WikDict's own `trans_list` column, which is pipe-separated. */
object TranslationFormatting {

    /**
     * "rano | ranu" → ["rano", "ranu"]. Blank fragments are dropped and
     * duplicates collapsed: the column is generated from Wiktionary and does
     * contain both, and a sheet listing the same word twice reads like a bug.
     */
    fun senses(transList: String?): List<String> =
        transList.orEmpty()
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}
