package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.mdhv.riverwip.data.db.SourceDao
import xyz.mdhv.riverwip.data.mapping.toDomain
import xyz.mdhv.riverwip.data.mapping.toEntity
import xyz.mdhv.riverwip.data.mapping.toHealth
import xyz.mdhv.riverwip.data.net.FeedProber
import xyz.mdhv.riverwip.data.net.ProbeResult
import xyz.mdhv.riverwip.model.FeedAutodiscovery
import xyz.mdhv.riverwip.model.Ids
import xyz.mdhv.riverwip.model.Opml
import xyz.mdhv.riverwip.model.ServiceDef
import xyz.mdhv.riverwip.model.Source
import xyz.mdhv.riverwip.model.SourceHealth
import xyz.mdhv.riverwip.model.SourceKindDetector
import xyz.mdhv.riverwip.model.Tier
import xyz.mdhv.riverwip.model.toSourceOrNull

/**
 * The sources repository (brief §P1). Coordinates persistence, feed probing, and
 * the starter/OPML paths. Every add is legible: add-by-URL reports exactly what
 * resolved, or offers the feeds a page declares.
 */
class SourceRepository(
    private val dao: SourceDao,
    private val probe: FeedProber,
    private val clock: () -> Long = { nowMillis() },
) {
    fun observeSources(): Flow<List<Source>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** The honest denominator (brief §1): count of the user's enabled sources. */
    fun observeEnabledCount(): Flow<Int> = dao.observeEnabledCount()

    /** One-shot enabled-source count (for a fetch-on-first-open decision, not a UI total). */
    suspend fun enabledCountOnce(): Int = dao.enabled().size

    /** Local source-health monitor (brief §P6) — kept on-device, never reported anywhere. */
    fun observeHealth(): Flow<List<SourceHealth>> =
        dao.observeAll().map { list -> val now = clock(); list.map { it.toHealth(now) } }

    sealed interface AddResult {
        data class Added(val source: Source) : AddResult
        /** The page declares several feeds; the user picks one. */
        data class NeedsChoice(val candidates: List<FeedAutodiscovery.DiscoveredFeed>) : AddResult
        data class Failed(val reason: String) : AddResult
    }

    /** Add by URL with autodiscovery. Builder endpoints (Google News/GDELT/Mastodon) probe as feeds directly. */
    suspend fun addByUrl(rawUrl: String): AddResult {
        val input = rawUrl.trim()
        if (input.isBlank()) return AddResult.Failed("Enter a URL.")
        return when (val r = probe.probe(input)) {
            is ProbeResult.Feed -> addResolvedFeed(r.url, r.title ?: r.url)
            is ProbeResult.Candidates ->
                if (r.feeds.size == 1) addResolvedFeed(r.feeds[0].url, r.feeds[0].title ?: r.feeds[0].url)
                else AddResult.NeedsChoice(r.feeds)
            is ProbeResult.NotAFeed -> AddResult.Failed(r.reason)
            is ProbeResult.Error -> AddResult.Failed(r.reason)
        }
    }

    /** Commit a concrete feed URL the user chose (or that resolved uniquely). */
    suspend fun addResolvedFeed(url: String, title: String): AddResult.Added {
        val kind = SourceKindDetector.detect(url)
        val source = Source(
            id = Ids.sourceId(kind, url),
            kind = kind,
            url = url,
            title = title.ifBlank { url },
            tier = Tier.USER,
            enabled = true,
            addedAt = clock(),
        )
        dao.upsert(source.toEntity())
        return AddResult.Added(source)
    }

    /** Add a one-click starter from the seed catalogue. */
    suspend fun addStarter(def: ServiceDef): AddResult {
        val source = def.toSourceOrNull(clock())?.copy(enabled = true)
            ?: return AddResult.Failed("${def.title} needs the builder or a key; open it to configure.")
        dao.upsert(source.toEntity())
        return AddResult.Added(source)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun remove(id: String) = dao.delete(id)

    suspend fun count(): Int = dao.count()

    /** OPML import: returns how many outlines were added. Existing ids are ignored. */
    suspend fun importOpml(xml: String): Int {
        val sources = Opml.parseToSources(xml, clock())
        if (sources.isEmpty()) return 0
        dao.insertAllIgnoring(sources.map { it.toEntity() })
        return sources.size
    }

    /** OPML export of the current source-set — the user's data is the user's file. */
    suspend fun exportOpml(): String =
        Opml.export(dao.allOnce().map { it.toDomain() })
}
