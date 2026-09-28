package xyz.mdhv.riverwip.data.repo

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The user's own data, exported as open JSON (owner's #9 + brief §4: the trace
 * is theirs, and it should be leaveable). Everything the app holds about *them*
 * — display preferences, the standing filter, their sources, their clippings,
 * and the coarse read log — assembled locally and handed back as a file. No
 * network, no account: this is an export, not a sync. API keys and secrets are
 * never included.
 */
class DataExporter(
    private val settingsRepository: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val clippingRepository: ClippingRepository,
    private val readEventRepository: ReadEventRepository,
) {
    private val pretty = Json { prettyPrint = true }

    suspend fun exportJson(exportedAt: Long): String {
        val settings = settingsRepository.observeSettings().first()
        val filter = settingsRepository.observeFilter().first()
        val sources = sourceRepository.observeSources().first()
        val clippings = clippingRepository.observeClippings().first()
        val reads = readEventRepository.allOnce()

        val root = buildJsonObject {
            put("app", "Nooz")
            put("schema", 1)
            put("exportedAt", exportedAt)
            putJsonObjectBlock("settings") {
                put("theme", settings.themeMode.key)
                put("readerFont", settings.readerFont.key)
                put("textScale", settings.textScale.key)
                put("showReadingTime", settings.showReadingTime)
                put("highlightLoadedLanguage", settings.highlightLoadedLanguage)
                put("immersiveReader", settings.immersiveReader)
            }
            putJsonObjectBlock("filter") {
                put("region", filter.region.key)
                put("topics", buildJsonArray { filter.topicKeys.forEach { add(it) } })
            }
            put("sources", buildJsonArray {
                sources.forEach { s ->
                    addJsonObject {
                        put("id", s.id)
                        put("kind", s.kind.name)
                        put("url", s.url)
                        put("title", s.title)
                        put("tier", s.tier.name)
                        put("enabled", s.enabled)
                        put("addedAt", s.addedAt)
                    }
                }
            })
            put("clippings", buildJsonArray {
                clippings.forEach { c ->
                    addJsonObject {
                        put("itemId", c.itemId)
                        put("title", c.title)
                        put("sourceTitle", c.sourceTitle)
                        put("author", c.author)
                        put("url", c.url)
                        put("topic", c.topicKey)
                        put("publishedAt", c.publishedAt)
                        put("savedAt", c.savedAt)
                        put("excerpt", c.excerpt)
                    }
                }
            })
            put("readEvents", buildJsonArray {
                reads.forEach { r ->
                    addJsonObject {
                        put("itemId", r.itemId)
                        put("openedAt", r.openedAt)
                        put("dwell", r.dwellBucket.key)
                        put("viaRiver", r.viaRiver)
                    }
                }
            })
        }
        return pretty.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putJsonObjectBlock(
    key: String,
    block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
) {
    put(key, buildJsonObject(block))
}
