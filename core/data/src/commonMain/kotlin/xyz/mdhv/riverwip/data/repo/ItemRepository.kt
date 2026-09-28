package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import xyz.mdhv.riverwip.data.db.ItemDao
import xyz.mdhv.riverwip.data.db.SourceDao
import xyz.mdhv.riverwip.data.db.SourceEntity
import xyz.mdhv.riverwip.data.mapping.toDomain
import xyz.mdhv.riverwip.data.mapping.toEntity
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.model.FeedUrls
import xyz.mdhv.riverwip.model.Ingest
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.Source
import xyz.mdhv.riverwip.model.SourceKind
import xyz.mdhv.riverwip.model.Tier

/**
 * Fetch + ingest + read the item stream (brief §P2). [FetchOutcome] surfaces
 * per-source failure tracking; a failure on one source never blocks the others
 * (brief §P2: "per-source failure tracking").
 */
class ItemRepository(
    private val sourceDao: SourceDao,
    private val itemDao: ItemDao,
    private val http: HttpClient,
    private val clock: () -> Long = { nowMillis() },
) {

    /** Items from the user's *currently enabled* sources — the content behind the honest denominator (brief §1). */
    fun observeItemsForEnabledSources(): Flow<List<Item>> =
        sourceDao.observeAll()
            .map { list -> list.filter { it.enabled }.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) flowOf(emptyList())
                else itemDao.observeForSources(ids).map { entities -> entities.map { it.toDomain() } }
            }

    data class FetchOutcome(val sourceId: String, val newItemCount: Int, val error: String? = null) {
        val succeeded: Boolean get() = error == null
    }

    /** Fetch + ingest every enabled source. One source's failure never blocks the rest. */
    suspend fun fetchAndIngestAllEnabled(): List<FetchOutcome> =
        sourceDao.enabled().map { fetchAndIngestOne(it) }

    private suspend fun fetchAndIngestOne(source: SourceEntity): FetchOutcome {
        val now = clock()
        return try {
            val resp = http.get(source.url, etag = source.etag, lastModified = source.lastModified)
            if (resp.notModified) {
                sourceDao.markFetchSuccess(source.id, source.etag, source.lastModified, now)
                return FetchOutcome(source.id, 0)
            }
            if (!resp.isSuccess) {
                val reason = "HTTP ${resp.code}"
                sourceDao.markFetchFailure(source.id, reason, now)
                return FetchOutcome(source.id, 0, reason)
            }
            val kind = SourceKind.fromKey(source.kind) ?: SourceKind.RSS
            val domainSource = Source(
                id = source.id, kind = kind, url = source.url, title = source.title,
                tier = Tier.fromKey(source.tier), enabled = source.enabled, addedAt = source.addedAt,
            )
            val items = Ingest.ingest(domainSource, resp.body, resp.contentType, now)
            itemDao.insertAllIgnoring(items.map { it.toEntity() })
            sourceDao.markFetchSuccess(source.id, resp.etag, resp.lastModified, now)
            FetchOutcome(source.id, items.size)
        } catch (e: Exception) {
            val reason = e.message ?: (e::class.simpleName ?: "Error")
            sourceDao.markFetchFailure(source.id, reason, now)
            FetchOutcome(source.id, 0, reason)
        }
    }

    /**
     * Historical backfill for the loom's date picker (the long-pending "fetch
     * content for any date" item). GDELT DOC 2.0 is the one catalogue provider
     * whose real API supports an absolute historical date query — RSS/Atom,
     * Google News RSS, Mastodon timelines, and the generic keyed `api` kind
     * have no such capability at all, so they are deliberately left untouched
     * here rather than faked. Re-fetches every *enabled* GDELT-kind source
     * for the half-open `[dayStartMillis, dayEndExclusiveMillis)` window,
     * using [FeedUrls.gdeltDocForRange] in place of the source's stored
     * (relative-`timespan`) URL, and merges any new items in exactly the way
     * a normal fetch does (same [Ingest], same conflict-ignoring insert).
     *
     * Deliberately does **not** touch etag/lastModified/lastFetchAt/failure
     * bookkeeping on the source row: those describe the source's regular
     * *live* poll cadence (and feed [SourceRepository.observeHealth]), while
     * this queries a different, absolute-date resource on demand — recording
     * it there would make a source's health status reflect a manual backfill
     * instead of its actual live-feed health.
     */
    suspend fun fetchHistoricalGdelt(dayStartMillis: Long, dayEndExclusiveMillis: Long): List<FetchOutcome> {
        val now = clock()
        return sourceDao.enabled()
            .filter { SourceKind.fromKey(it.kind) == SourceKind.GDELT }
            .map { source -> fetchHistoricalGdeltOne(source, dayStartMillis, dayEndExclusiveMillis, now) }
    }

    private suspend fun fetchHistoricalGdeltOne(
        source: SourceEntity,
        dayStartMillis: Long,
        dayEndExclusiveMillis: Long,
        now: Long,
    ): FetchOutcome {
        val url = FeedUrls.gdeltDocForRange(source.url, dayStartMillis, dayEndExclusiveMillis)
            ?: return FetchOutcome(source.id, 0, "Not a recognized GDELT DOC 2.0 URL")
        return try {
            val resp = http.get(url)
            if (!resp.isSuccess) return FetchOutcome(source.id, 0, "HTTP ${resp.code}")
            val domainSource = Source(
                id = source.id, kind = SourceKind.GDELT, url = source.url, title = source.title,
                tier = Tier.fromKey(source.tier), enabled = source.enabled, addedAt = source.addedAt,
            )
            val items = Ingest.ingest(domainSource, resp.body, resp.contentType, now)
            itemDao.insertAllIgnoring(items.map { it.toEntity() })
            FetchOutcome(source.id, items.size)
        } catch (e: Exception) {
            FetchOutcome(source.id, 0, e.message ?: (e::class.simpleName ?: "Error"))
        }
    }

    /** Retention: prune Item rows older than [days] (default 60, brief §4). Aggregates survive independently. */
    suspend fun pruneOlderThan(days: Int = 60) {
        val cutoff = clock() - days.toLong() * 24 * 60 * 60 * 1000
        itemDao.pruneOlderThan(cutoff)
    }

    suspend fun allItemsOnce(): List<Item> = itemDao.allOnce().map { it.toDomain() }

    suspend fun itemsInRange(fromMillis: Long, toMillis: Long): List<Item> =
        itemDao.inRange(fromMillis, toMillis).map { it.toDomain() }

    suspend fun markFullTextCached(itemId: String, cached: Boolean) = itemDao.setFullTextCached(itemId, cached)

    suspend fun byId(itemId: String): Item? = itemDao.byId(itemId)?.toDomain()
}
