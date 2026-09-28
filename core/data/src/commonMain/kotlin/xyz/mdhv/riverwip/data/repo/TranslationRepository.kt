package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.IoDispatcher
import xyz.mdhv.riverwip.data.DataPlatform
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import xyz.mdhv.riverwip.model.TranslationCatalog
import xyz.mdhv.riverwip.model.TranslationFormatting
import xyz.mdhv.riverwip.model.TranslationOption
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import okio.Path
import xyz.mdhv.riverwip.data.net.HttpClient

/**
 * Abstraction over pulling a dictionary file down, so [TranslationRepository]
 * is unit-testable with a fake — the same shape as [ArticleFetcher].
 */
interface TranslationDownloader {
    suspend fun fetchTo(url: String, dest: Path)
}

/**
 * Word-level translation (owner's ask): long-press a word while reading in a
 * language that isn't yours and see it in one that is.
 *
 * Same arrangement as [DictionaryRepository] — one dictionary installed at a
 * time, downloaded only when the reader asks, then queried entirely offline —
 * but stored as **SQLite rather than a flat JSON map**. That is a deliberate
 * departure: the Webster's map is held wholly in memory (its own doc comment
 * calls that "a considered v1 trade-off"), and doing the same to a 26 MB
 * bilingual dictionary would be a considerably worse one. Queried on disk, a
 * lookup costs an indexed row read and no resident memory at all.
 *
 * Nothing about a lookup leaves the device, which is the same promise the
 * dictionary lens already makes: which words a reader looks up is nobody's
 * business, and there is no request to intercept in the first place.
 */
class TranslationRepository(
    private val platform: DataPlatform,
    private val downloader: TranslationDownloader = HttpTranslationDownloader(platform),
) {
    private val fs get() = platform.fileSystem

    val options: List<TranslationOption> get() = TranslationCatalog.options

    private val installedKey = stringPreferencesKey("installed_id")

    fun observeInstalledId(): Flow<String?> = platform.stores.translation.data.map { it[installedKey] }

    suspend fun installedOption(): TranslationOption? =
        TranslationCatalog.byId(observeInstalledId().first())

    private fun translationsDir(): Path = platform.filesDir / "translations"

    private fun fileFor(id: String): Path = translationsDir() / "$id.sqlite3"

    /**
     * Fetch a dictionary and make it the active one, replacing any previous.
     *
     * Downloads to a temporary file first: a half-written database that
     * happened to keep the real name would be indistinguishable from a good
     * one at query time, and would fail as a corrupt file on every lookup
     * forever rather than as a failed download once.
     */
    suspend fun download(option: TranslationOption): Result<Unit> = withContext(IoDispatcher) {
        runCatching {
            val dir = translationsDir().also { fs.createDirectories(it) }
            val dest = fileFor(option.id)
            val staging = dir / "${option.id}.part"
            try {
                downloader.fetchTo(option.downloadUrl, staging)
                require((fs.metadataOrNull(staging)?.size ?: 0L) > 0) { "empty download" }
                indexForLookup(staging)
                fs.delete(dest, mustExist = false)
                fs.atomicMove(staging, dest)
            } finally {
                fs.delete(staging, mustExist = false)
            }
            // One installed at a time, like the dictionary: the others are the
            // reader's storage, not ours to keep spending.
            fs.list(dir).forEach { if (it != dest) fs.delete(it, mustExist = false) }
            platform.stores.translation.edit { it[installedKey] = option.id }
            Unit
        }
    }

    /** Forget and delete the installed dictionary. */
    suspend fun remove(): Unit = withContext(IoDispatcher) {
        if (fs.exists(translationsDir())) fs.list(translationsDir()).forEach { fs.delete(it, mustExist = false) }
        platform.stores.translation.edit { it.remove(installedKey) }
    }

    /**
     * Translations for a word, or an empty list when there is no installed
     * dictionary or no entry. Never throws: a corrupt or half-written file is
     * a reason to show "no translation", not to take down the reader.
     */
    suspend fun translate(word: String): List<String> = withContext(IoDispatcher) {
        val id = observeInstalledId().first() ?: return@withContext emptyList()
        val file = fileFor(id)
        if (fs.metadataOrNull(file)?.isRegularFile != true) return@withContext emptyList()
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        runCatching {
            val db = platform.sqliteDriver.open(file.toString())
            try {
                var found: List<String> = emptyList()
                for (candidate in candidates(trimmed)) {
                    val hit = lookup(db, candidate)
                    if (hit.isNotEmpty()) { found = hit; break }
                }
                found
            } finally {
                db.close()
            }
        }.getOrDefault(emptyList())
    }

    private fun lookup(db: SQLiteConnection, word: String): List<String> {
        val stmt = db.prepare("SELECT trans_list FROM simple_translation WHERE written_rep = ? LIMIT 1")
        try {
            stmt.bindText(1, word)
            return if (stmt.step()) TranslationFormatting.senses(stmt.getText(0)) else emptyList()
        } finally {
            stmt.close()
        }
    }

    /**
     * Spellings to try, in order. WikDict stores headwords in their own casing
     * and matches are exact, so a word tapped at the start of a sentence
     * ("Water") finds nothing unless its lowercase form is tried too.
     */
    private fun candidates(word: String): List<String> = listOf(
        word,
        word.lowercase(),
        word.replaceFirstChar { it.uppercase() },
    ).distinct()

    /**
     * WikDict ships these tables **without an index on `written_rep`**, so
     * every lookup would otherwise scan the whole table — tolerable at 4,000
     * rows, not at the hundreds of thousands in the larger pairs. Built once,
     * here, rather than on each query.
     */
    private fun indexForLookup(file: Path) {
        val db = platform.sqliteDriver.open(file.toString())
        try {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_simple_translation_written_rep " +
                    "ON simple_translation(written_rep)",
            )
        } finally {
            db.close()
        }
    }

}

/** The real downloader: streams straight to disk, following redirects. */
class HttpTranslationDownloader(
    private val platform: DataPlatform,
    private val http: HttpClient = HttpClient(),
) : TranslationDownloader {

    // Deliberately no gzip: these are SQLite files served as-is, and a
    // transparently-decoded stream would have to be written to disk before it
    // could be opened anyway.
    override suspend fun fetchTo(url: String, dest: Path) {
        http.download(url, dest, platform.fileSystem, what = "translation dictionary", acceptGzip = false)
    }
}
