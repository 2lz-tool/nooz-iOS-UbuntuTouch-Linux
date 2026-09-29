package xyz.mdhv.riverwip.bridge

import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import xyz.mdhv.riverwip.data.IngestCycle
import xyz.mdhv.riverwip.data.RiverData
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.model.DwellBucket

/**
 * The reading core, without any C in it: everything the front end can ask, as plain Kotlin returning
 * [JsonElement]s. [NoozApi] is the thin C-callable layer over this class, so this is what the
 * tests exercise directly.
 */
class NoozCore(platform: LinuxDataPlatform) {
    private val data = RiverData.create(platform)
    private val ingest = IngestCycle(data.itemRepository, data.weeklyAggregateRepository, data.articleRepository)

    private fun ok(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) =
        buildJsonObject { put("ok", true); build() }

    private fun failed(reason: String) = buildJsonObject { put("ok", false); put("error", reason) }

    suspend fun addSource(url: String): JsonElement = when (val r = data.sourceRepository.addByUrl(url)) {
        is SourceRepository.AddResult.Added -> ok { put("id", r.source.id); put("title", r.source.title) }
        is SourceRepository.AddResult.NeedsChoice -> ok {
            put("choose", buildJsonArray { r.candidates.forEach { add(buildJsonObject { put("url", it.url); put("title", it.title ?: it.url) }) } })
        }
        is SourceRepository.AddResult.Failed -> failed(r.reason)
    }

    suspend fun addResolvedFeed(url: String, title: String): JsonElement {
        val added = data.sourceRepository.addResolvedFeed(url, title)
        return ok { put("id", added.source.id); put("title", added.source.title) }
    }

    suspend fun sources(): JsonElement {
        val health = data.sourceRepository.observeHealth().first().associateBy { it.sourceId }
        return buildJsonArray {
            for (s in data.sourceRepository.observeSources().first()) {
                add(buildJsonObject {
                    put("id", s.id); put("title", s.title); put("url", s.url); put("enabled", s.enabled)
                    put("status", health[s.id]?.status?.name?.lowercase() ?: "new")
                    put("error", health[s.id]?.lastError ?: "")
                })
            }
        }
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean): JsonElement {
        data.sourceRepository.setEnabled(id, enabled)
        return ok()
    }

    suspend fun removeSource(id: String): JsonElement {
        data.sourceRepository.remove(id)
        return ok()
    }

    /** Fetch every enabled source, then roll up and prune. Blocks until done. */
    suspend fun refresh(): JsonElement {
        val outcomes = try {
            data.itemRepository.fetchAndIngestAllEnabled()
        } catch (e: Exception) {
            return failed(e.message ?: "refresh failed")
        }
        runCatching {
            data.weeklyAggregateRepository.recompute()
            data.itemRepository.pruneOlderThan()
            data.articleRepository.pruneIndexOrphans()
        }
        return ok {
            put("new", outcomes.sumOf { it.newItemCount })
            put("failedSources", outcomes.count { !it.succeeded })
        }
    }

    suspend fun items(limit: Int): JsonElement {
        val titles = data.sourceRepository.observeSources().first().associate { it.id to it.title }
        val readIds = data.readEventRepository.allOnce().map { it.itemId }.toSet()
        return buildJsonArray {
            data.itemRepository.observeItemsForEnabledSources().first()
                .sortedByDescending { it.publishedAt }
                .take(limit)
                .forEach { item ->
                    add(buildJsonObject {
                        put("id", item.id)
                        put("title", item.title)
                        put("source", titles[item.sourceId] ?: "")
                        put("author", item.author ?: "")
                        put("publishedAt", item.publishedAt)
                        put("summary", item.summary ?: "")
                        put("url", item.canonicalUrl)
                        put("image", item.imageUrl ?: "")
                        put("topic", item.topics.firstOrNull()?.topic?.key ?: "")
                        put("read", item.id in readIds)
                    })
                }
        }
    }

    /** The article body: the cached/extracted full text if there is one, else the feed's own summary. */
    suspend fun article(id: String): JsonElement {
        val item = data.itemRepository.byId(id) ?: return failed("unknown article")
        val text = runCatching { data.articleRepository.textFor(item.id, item.canonicalUrl) }.getOrNull()
        return ok {
            put("id", item.id)
            put("title", item.title)
            put("url", item.canonicalUrl)
            put("full", text != null)
            put("paragraphs", JsonArray((text?.paragraphs ?: listOfNotNull(item.summary?.takeIf { it.isNotBlank() })).map(::JsonPrimitive)))
        }
    }

    suspend fun markRead(id: String): JsonElement {
        data.readEventRepository.record(id, DwellBucket.READ, viaRiver = false)
        return ok()
    }
}

