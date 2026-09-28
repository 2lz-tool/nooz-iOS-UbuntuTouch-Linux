package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.IoDispatcher
import xyz.mdhv.riverwip.data.DataPlatform
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import xyz.mdhv.riverwip.model.DictionaryCatalog
import xyz.mdhv.riverwip.model.DictionaryOption
import okio.Path
import okio.buffer
import okio.use
import kotlin.concurrent.Volatile
import xyz.mdhv.riverwip.data.net.HttpClient

/**
 * The dictionary lens's data layer (owner's Kindle-style definitions). Two
 * pieces:
 *  - a bundled, permissively-licensed common-word set (top-30k frequency list)
 *    for the offline obscure-word gate — no network, always available;
 *  - a downloaded dictionary (the owner's "one-click download") for the
 *    definitions themselves. Streaming download bypasses [HttpClient]'s small
 *    body cap; lookup lazily parses the flat `{WORD: definition}` JSON once and
 *    caches it. Local only — nothing synced or transmitted.
 *
 * The 22 MB Webster's map is held in memory once loaded (a considered v1
 * trade-off for O(1) lookups); an on-disk index is a later refinement.
 */
class DictionaryRepository(
    private val platform: DataPlatform,
    private val http: HttpClient = HttpClient(),
) {
    private val fs get() = platform.fileSystem

    val options: List<DictionaryOption> get() = DictionaryCatalog.options

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---- common-word gate (bundled asset) ----
    @Volatile private var commonCache: Set<String>? = null
    private val commonMutex = Mutex()

    suspend fun commonWords(): Set<String> {
        commonCache?.let { return it }
        return commonMutex.withLock {
            commonCache ?: withContext(IoDispatcher) {
                (platform.readAsset("common_words.txt") ?: "").lineSequence()
                    .map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
            }.also { commonCache = it }
        }
    }

    // ---- downloaded dictionary ----
    private val downloadedKey = stringPreferencesKey("downloaded_id")

    fun observeDownloadedId(): Flow<String?> = platform.stores.dictionary.data.map { it[downloadedKey] }

    private fun dictionariesDir(): Path = platform.filesDir / "dictionaries"
    private fun fileFor(id: String): Path = dictionariesDir() / "$id.json"

    /** Stream a chosen dictionary to storage and make it the active one (replacing any previous). */
    suspend fun download(option: DictionaryOption): Result<Unit> = withContext(IoDispatcher) {
        runCatching {
            val dir = dictionariesDir().also { fs.createDirectories(it) }
            val tmp = dir / "${option.id}.download"
            http.download(option.downloadUrl, tmp, fs, what = "dictionary", acceptGzip = true)
            val dest = fileFor(option.id)
            fs.delete(dest, mustExist = false)
            fs.atomicMove(tmp, dest)
            // Keep exactly one active dictionary.
            fs.list(dir).forEach { if (it.name != dest.name) fs.delete(it, mustExist = false) }
            platform.stores.dictionary.edit { it[downloadedKey] = option.id }
            lookupCache = null
            lookupCacheId = null
        }
    }

    // ---- lookup ----
    @Volatile private var lookupCache: Map<String, String>? = null
    @Volatile private var lookupCacheId: String? = null
    private val lookupMutex = Mutex()

    suspend fun define(word: String): String? {
        val id = observeDownloadedId().first() ?: return null
        val map = ensureLoaded(id) ?: return null
        return lookup(map, word) ?: morphology(word).firstNotNullOfOrNull { lookup(map, it) }
    }

    /** Try a word in the dictionary's own casing conventions (Webster's keys are UPPERCASE). */
    private fun lookup(map: Map<String, String>, w: String): String? =
        map[w] ?: map[w.lowercase()] ?: map[w.uppercase()] ?: map[w.replaceFirstChar { it.uppercase() }]

    /**
     * Cheap morphological fallbacks so long-pressing an inflected form still
     * lands its base entry ("running" → "run", "quibbles" → "quibble"). Not a
     * stemmer — just the handful of English endings that cover most reading.
     */
    private fun morphology(word: String): List<String> {
        val w = word.lowercase()
        val out = ArrayList<String>(6)
        fun add(s: String) { if (s.length >= 2) out.add(s) }
        when {
            w.endsWith("ies") && w.length > 4 -> add(w.dropLast(3) + "y")
            w.endsWith("es") && w.length > 3 -> { add(w.dropLast(2)); add(w.dropLast(1)) }
            w.endsWith("s") && !w.endsWith("ss") && w.length > 3 -> add(w.dropLast(1))
        }
        when {
            w.endsWith("ing") && w.length > 5 -> { add(w.dropLast(3)); add(w.dropLast(3) + "e") }
            w.endsWith("ed") && w.length > 4 -> { add(w.dropLast(2)); add(w.dropLast(1)) }
            w.endsWith("ly") && w.length > 4 -> add(w.dropLast(2))
        }
        return out
    }

    private suspend fun ensureLoaded(id: String): Map<String, String>? {
        if (lookupCacheId == id) return lookupCache
        return lookupMutex.withLock {
            if (lookupCacheId == id) return lookupCache
            val f = fileFor(id)
            val map = if (!fs.exists(f)) {
                null
            } else {
                withContext(IoDispatcher) {
                    runCatching {
                        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), fs.source(f).buffer().use { it.readUtf8() })
                    }.getOrNull()
                }
            }
            lookupCache = map
            lookupCacheId = id
            map
        }
    }
}
