package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.nowMillis
import xyz.mdhv.riverwip.data.DataPlatform
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.model.Catalogue
import xyz.mdhv.riverwip.model.Starters

/**
 * The catalogue-sensing layer (brief §P6): lets Tier A/B source definitions and
 * free-tier limits refresh without an app release.
 *
 * There is deliberately **no baked-in default URL**. Brief §0 requires every
 * remote URL this app talks to be verified live at build time, and "the
 * provider-catalogue repo" `catalogue.json` is meant to come from is an
 * external project this build has no access to and cannot verify — shipping a
 * guessed URL would violate that standard exactly the way an unverified feed
 * would. The user supplies the URL (and can clear it back to the built-in,
 * verified [Starters.seed] at any time); nothing is fetched unless the user
 * explicitly asks — this is a pull, on demand, never a background job, and
 * never something the device reports to anywhere on its own.
 */
class CatalogueRepository(
    private val platform: DataPlatform,
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private object Keys {
        val URL = stringPreferencesKey("catalogue_url")
        val RAW_JSON = stringPreferencesKey("catalogue_raw_json")
        val FETCHED_AT = longPreferencesKey("catalogue_fetched_at")
    }

    sealed interface RefreshResult {
        data class Success(val catalogue: Catalogue) : RefreshResult
        data class Failed(val reason: String) : RefreshResult
    }

    fun observeCatalogueUrl(): Flow<String?> = platform.stores.catalogue.data.map { it[Keys.URL] }

    fun observeLastRefreshedAt(): Flow<Long?> = platform.stores.catalogue.data.map { it[Keys.FETCHED_AT] }

    /** Falls back to the verified built-in seed whenever no remote catalogue has been loaded (or it fails to parse). */
    fun observeCatalogue(): Flow<Catalogue> = platform.stores.catalogue.data.map { prefs ->
        val raw = prefs[Keys.RAW_JSON] ?: return@map Starters.seed
        runCatching { json.decodeFromString(Catalogue.serializer(), raw) }.getOrDefault(Starters.seed)
    }

    suspend fun setCatalogueUrl(url: String?) {
        platform.stores.catalogue.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(Keys.URL) else prefs[Keys.URL] = url.trim()
        }
    }

    /** Reverts to the built-in verified starters, discarding any cached remote catalogue. */
    suspend fun clearCatalogue() {
        platform.stores.catalogue.edit { prefs ->
            prefs.remove(Keys.URL)
            prefs.remove(Keys.RAW_JSON)
            prefs.remove(Keys.FETCHED_AT)
        }
    }

    suspend fun refresh(clock: () -> Long = { nowMillis() }): RefreshResult {
        val url = observeCatalogueUrl().first()
            ?: return RefreshResult.Failed("No catalogue URL set; set one to refresh Tier A/B definitions remotely.")
        return try {
            val resp = http.get(url)
            if (!resp.isSuccess) return RefreshResult.Failed("HTTP ${resp.code}")
            val parsed = json.decodeFromString(Catalogue.serializer(), resp.body)
            platform.stores.catalogue.edit { prefs ->
                prefs[Keys.RAW_JSON] = resp.body
                prefs[Keys.FETCHED_AT] = clock()
            }
            RefreshResult.Success(parsed)
        } catch (e: Exception) {
            RefreshResult.Failed(e.message ?: (e::class.simpleName ?: "Error"))
        }
    }
}
