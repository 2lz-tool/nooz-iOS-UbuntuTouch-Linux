package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.IoDispatcher
import xyz.mdhv.riverwip.data.nowMillis
import xyz.mdhv.riverwip.data.DataPlatform
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.mdhv.riverwip.data.net.HttpClient
import okio.Path
import okio.buffer
import okio.use

@Serializable
data class CatalogueModel(
    val id: String,
    val name: String,
    val kind: String,
    val sizeBytes: Long,
    val downloadUrl: String? = null,
    val hfRepo: String? = null,
    val sha256: String? = null,
    val policySafe: Boolean = true,
    val verifiedAt: String? = null,
    val note: String? = null,
    /** Links companion files that must all be present together, e.g. a TTS model plus its required voice pack. */
    val groupId: String? = null,
)

@Serializable
private data class CatalogueFile(
    val schemaVersion: Int = 1,
    val lastUpdated: String = "",
    val models: List<CatalogueModel> = emptyList(),
)

/** A model's in-flight download state, keyed by id; absent from the map means idle/not-started. */
sealed interface ModelDownloadState {
    data class Downloading(val progress: Float) : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Failed(val reason: String) : ModelDownloadState
}

/**
 * The real, one-click downloadable model list (owner's #18 follow-up): reads
 * `ai-catalogue/models.json` — this repo's own honestly-maintained, live-probed
 * catalogue (see `ai-catalogue/README.md`) — instead of the placeholder,
 * always-empty entries the lens's on-device provider used to hardcode. Only
 * `policySafe` entries of the requested `kind` (default `LLM_GGUF`; Nooz Cast
 * asks for `TTS_ONNX` instead, its own independent gate) with a verified
 * `downloadUrl` are ever offered; a `null` URL is never rendered as
 * "downloadable" (the catalogue's own honesty rule 4).
 *
 * A bundled snapshot is the offline/first-run default; refreshing is an
 * explicit, user-initiated action (never automatic), matching the catalogue's
 * consumer contract.
 */
class ModelCatalogueRepository(
    private val platform: DataPlatform,
    private val http: HttpClient = HttpClient(),
) {
    private val fs get() = platform.fileSystem
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private object Keys {
        val REFRESHED_JSON = stringPreferencesKey("refreshed_models_json")
        val REFRESHED_AT = longPreferencesKey("refreshed_at")
    }

    fun observeLastRefreshedAt(): Flow<Long?> = platform.stores.modelCatalogue.data.map { it[Keys.REFRESHED_AT] }

    /** The bundled snapshot, or a previously-fetched refresh if one exists and still parses. */
    fun observeCatalogue(): Flow<List<CatalogueModel>> = platform.stores.modelCatalogue.data.map { prefs ->
        val raw = prefs[Keys.REFRESHED_JSON]
        val parsed = raw?.let { runCatching { json.decodeFromString(CatalogueFile.serializer(), it) }.getOrNull() }
        (parsed ?: bundled()).models
    }

    private fun bundled(): CatalogueFile = runCatching {
        json.decodeFromString(CatalogueFile.serializer(), platform.readAsset("ai_catalogue_models.json") ?: error("bundled catalogue missing"))
    }.getOrDefault(CatalogueFile())

    /**
     * Only what this app can actually offer for [kind]: official/licensed
     * weights, a verified mirror. Companion groups (catalogue `groupId` — a TTS
     * model plus its required voice pack) collapse to ONE row, the group's
     * anchor (its first eligible member); the companions ride along on that
     * single download ([downloadGroup]) instead of appearing as separate,
     * individually-useless rows a reader would have to discover and tap too.
     */
    fun downloadable(models: List<CatalogueModel>, kind: String = "LLM_GGUF"): List<CatalogueModel> {
        val seenGroups = mutableSetOf<String>()
        return models
            .filter { it.kind == kind && it.policySafe && !it.downloadUrl.isNullOrBlank() }
            .filter { model ->
                val gid = model.groupId ?: return@filter true // ungrouped entry: always its own row
                seenGroups.add(gid) // true only for the first eligible member (the anchor); companions return false
            }
    }

    /**
     * Every catalogue entry that must be on disk together with [model] — the
     * companion group named by its `groupId` (e.g. Kokoro's ONNX model *and* its
     * paired voice `.bin`), or just [model] itself when it stands alone. The TTS
     * engine needs the whole group present to load, so a single tap fetches all
     * of them via [downloadGroup]; [models] is the live catalogue to resolve
     * against.
     */
    fun groupMembers(models: List<CatalogueModel>, model: CatalogueModel): List<CatalogueModel> {
        val gid = model.groupId ?: return listOf(model)
        return models.filter { it.groupId == gid }.ifEmpty { listOf(model) }
    }

    suspend fun refresh(clock: () -> Long = { nowMillis() }): Result<Int> = withContext(IoDispatcher) {
        runCatching {
            val resp = http.get(DEFAULT_CATALOGUE_URL)
            if (!resp.isSuccess) error("HTTP ${resp.code}")
            val parsed = json.decodeFromString(CatalogueFile.serializer(), resp.body)
            platform.stores.modelCatalogue.edit { prefs ->
                prefs[Keys.REFRESHED_JSON] = resp.body
                prefs[Keys.REFRESHED_AT] = clock()
            }
            parsed.models.size
        }
    }

    // ---- download / delete ----

    private fun modelsDir(): Path = (platform.filesDir / "models").also { fs.createDirectories(it) }

    /** Extension follows the real download, not a hardcoded assumption — GGUF isn't the only [CatalogueModel.kind] on disk anymore (e.g. Kokoro's `.onnx`/`.bin`). */
    fun fileFor(model: CatalogueModel): Path =
        modelsDir() / "${model.id}.${model.downloadUrl?.substringAfterLast('.', "bin") ?: "bin"}"
    fun isDownloaded(model: CatalogueModel): Boolean = fs.exists(fileFor(model))

    /** Bytes free where the model would land, so a multi-gigabyte download can be refused honestly up front. */
    fun availableStorageBytes(): Long = platform.usableSpaceBytes(modelsDir())

    suspend fun download(model: CatalogueModel, onProgress: (Float) -> Unit): Result<Unit> =
        withContext(IoDispatcher) {
            runCatching {
                val url = model.downloadUrl ?: error("No verified download URL for ${model.name}")
                val dir = modelsDir()
                val tmp = dir / "${model.id}.download"
                var lastReported = -1
                http.download(url, tmp, fs, what = "model") { read, contentLength ->
                    val total = if (contentLength > 0) contentLength else model.sizeBytes
                    if (total > 0) {
                        val pct = (read.toFloat() / total).coerceIn(0f, 1f)
                        val reportable = (pct * 100).toInt()
                        if (reportable != lastReported) {
                            lastReported = reportable
                            onProgress(pct)
                        }
                    }
                }
                onProgress(1f)
                val dest = fileFor(model)
                fs.delete(dest, mustExist = false)
                fs.atomicMove(tmp, dest)
            }
        }

    fun delete(model: CatalogueModel) {
        fs.delete(fileFor(model), mustExist = false)
    }

    /**
     * Fetch a whole companion group (from [groupMembers]) so one Cast download
     * yields a complete, loadable install: the TTS model *and* its paired voice
     * pack, not just whichever row was tapped. Members already on disk are
     * skipped; the rest download sequentially, each to its own [fileFor] path
     * (the exact filenames the engine loads). Progress is blended by real bytes
     * — a voice pack is <1% of the model, so a naive per-file split would jump
     * the bar. Fails fast if any member fails.
     */
    suspend fun downloadGroup(members: List<CatalogueModel>, onProgress: (Float) -> Unit): Result<Unit> =
        withContext(IoDispatcher) {
            runCatching {
                val pending = members.filter { !isDownloaded(it) }
                if (pending.isEmpty()) {
                    onProgress(1f)
                    return@runCatching
                }
                val totalBytes = pending.sumOf { it.sizeBytes.coerceAtLeast(1L) }.toFloat()
                var doneBytes = 0L
                for (member in pending) {
                    val weight = member.sizeBytes.coerceAtLeast(1L)
                    download(member) { p ->
                        onProgress(((doneBytes + p * weight) / totalBytes).coerceIn(0f, 1f))
                    }.getOrThrow()
                    doneBytes += weight
                }
                onProgress(1f)
            }
        }

    /** A companion group counts as downloaded only when every member (model + voice) is present — Kokoro can't load with either missing. */
    fun isGroupDownloaded(members: List<CatalogueModel>): Boolean = members.all { isDownloaded(it) }

    fun deleteGroup(members: List<CatalogueModel>) = members.forEach { delete(it) }

    companion object {
        /**
         * This constellation's accepted interim convention (D16): the catalogue's
         * canonical home is this repo's own default branch — not `main`, which
         * stays an empty placeholder here — mirroring how Aarso already points at
         * it. If that branch is ever renamed, repoint this alongside Aarso's
         * `SessionStore.DEFAULT_MC_URL`.
         */
        const val DEFAULT_CATALOGUE_URL: String =
            "https://raw.githubusercontent.com/mbaliga/nooz/claude/app-build-d1f9s6/ai-catalogue/models.json"
    }
}
