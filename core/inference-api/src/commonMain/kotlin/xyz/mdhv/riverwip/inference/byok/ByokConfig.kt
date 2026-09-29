package xyz.mdhv.riverwip.inference.byok

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Bring-your-own-key config (owner's #18): the user's own OpenAI-compatible
 * endpoint, key, and model. This routes a rewrite to *their* provider, on their
 * terms — always cloud-marked ([xyz.mdhv.riverwip.inference.Provenance.CLOUD]),
 * never on by default, and never included in the data export.
 */
data class ByokConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
) {
    val isComplete: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** The chat-completions endpoint for this base URL (OpenAI-compatible). */
    val chatCompletionsUrl: String
        get() {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }
}

/**
 * A tiny synchronous string store for secrets-adjacent settings. Android backs it with the
 * private `byok_config` SharedPreferences the app has always used (so an existing key
 * survives the port); desktop and native use [FileKeyValueStore]. It is also the seam where
 * a platform keystore (Android Keystore, libsecret, the iOS Keychain) plugs in.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(entries: Map<String, String>)
    fun clear()
}

/** A [KeyValueStore] in one small JSON file. Callers own restricting the file's permissions where the platform can. */
class FileKeyValueStore(private val fileSystem: FileSystem, private val path: Path) : KeyValueStore {
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    private fun read(): Map<String, String> = try {
        if (!fileSystem.exists(path)) emptyMap()
        else Json.decodeFromString(serializer, fileSystem.read(path) { readUtf8() })
    } catch (_: Exception) {
        emptyMap()
    }

    override fun get(key: String): String? = read()[key]

    override fun put(entries: Map<String, String>) {
        path.parent?.let { fileSystem.createDirectories(it) }
        val tmp = path.parent?.resolve(path.name + ".tmp") ?: (path.name + ".tmp").toPath()
        fileSystem.write(tmp) { writeUtf8(Json.encodeToString(serializer, read() + entries)) }
        fileSystem.atomicMove(tmp, path)
    }

    override fun clear() {
        fileSystem.delete(path, mustExist = false)
    }
}

/**
 * Stores the BYOK config. Local, private storage -- the key never leaves the device and is
 * excluded from the data export. NOTE (follow-up): move the key itself into the platform
 * keystore before shipping a release; plain private storage is the v1 stopgap and is
 * logged in STATE.md.
 */
class ByokConfigStore(private val store: KeyValueStore) {

    fun load(): ByokConfig = ByokConfig(
        baseUrl = store.get(KEY_URL).orEmpty(),
        apiKey = store.get(KEY_KEY).orEmpty(),
        model = store.get(KEY_MODEL).orEmpty(),
    )

    fun save(config: ByokConfig) {
        store.put(
            mapOf(
                KEY_URL to config.baseUrl.trim(),
                KEY_KEY to config.apiKey.trim(),
                KEY_MODEL to config.model.trim(),
            ),
        )
    }

    fun clear() = store.clear()

    /** True when there's enough to attempt a call (checked by the provider's isAvailable). */
    val isConfigured: Boolean get() = load().isComplete

    companion object {
        private const val KEY_URL = "base_url"
        private const val KEY_KEY = "api_key"
        private const val KEY_MODEL = "model"
    }
}
