package xyz.mdhv.riverwip.model

/**
 * The format a downloaded dictionary is stored in. Only [FLAT_JSON] — a single
 * `{ "WORD": "definition" }` object — is looked up today; richer formats
 * (WordNet, dictd, Wiktionary JSONL) are catalogued elsewhere but not yet
 * parsed, so the catalogue below lists only what the app can actually read.
 */
enum class DictionaryFormat { FLAT_JSON }

/**
 * One downloadable dictionary (owner's "user selects which dictionary to
 * download, one-click"). Every [downloadUrl] is a real, live-verified source;
 * definitions are never bundled or fabricated.
 */
data class DictionaryOption(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val license: String,
    val sizeHuman: String,
    val format: DictionaryFormat,
)

object DictionaryCatalog {
    val options: List<DictionaryOption> = listOf(
        DictionaryOption(
            id = "websters-1913",
            name = "Webster's 1913",
            description = "The classic public-domain American dictionary, about 86,000 words with full prose definitions.",
            downloadUrl = "https://raw.githubusercontent.com/matthewreagan/WebstersEnglishDictionary/master/dictionary.json",
            license = "Public domain (1913 text); MIT (compilation)",
            sizeHuman = "22 MB",
            format = DictionaryFormat.FLAT_JSON,
        ),
    )

    fun byId(id: String?): DictionaryOption? = id?.let { key -> options.firstOrNull { it.id == key } }
}
