package xyz.mdhv.riverwip.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.mdhv.riverwip.data.db.SourceDao
import xyz.mdhv.riverwip.data.db.SourceEntity
import xyz.mdhv.riverwip.data.net.FeedProber
import xyz.mdhv.riverwip.data.net.ProbeResult
import xyz.mdhv.riverwip.model.FeedAutodiscovery
import kotlin.test.Test

/** In-memory fake DAO — lets the repository be tested as pure JVM logic. */
private class FakeSourceDao : SourceDao {
    val store = MutableStateFlow<List<SourceEntity>>(emptyList())
    override fun observeAll(): Flow<List<SourceEntity>> = store
    override fun observeEnabledCount(): Flow<Int> = store.map { l -> l.count { it.enabled } }
    override suspend fun allOnce(): List<SourceEntity> = store.value
    override suspend fun enabled(): List<SourceEntity> = store.value.filter { it.enabled }
    override suspend fun byId(id: String): SourceEntity? = store.value.firstOrNull { it.id == id }
    override suspend fun upsert(source: SourceEntity) {
        store.value = store.value.filterNot { it.id == source.id } + source
    }
    override suspend fun insertAllIgnoring(sources: List<SourceEntity>): List<Long> {
        val existing = store.value.map { it.id }.toSet()
        store.value = store.value + sources.filterNot { it.id in existing }
        return emptyList()
    }
    override suspend fun setEnabled(id: String, enabled: Boolean) {
        store.value = store.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }
    override suspend fun markFetchSuccess(id: String, etag: String?, lastModified: String?, fetchedAt: Long) {}
    override suspend fun markFetchFailure(id: String, error: String, fetchedAt: Long) {}
    override suspend fun delete(id: String) { store.value = store.value.filterNot { it.id == id } }
    override suspend fun count(): Int = store.value.size
}

private class FakeProber(private val result: ProbeResult) : FeedProber {
    var lastInput: String? = null
    override suspend fun probe(inputUrl: String): ProbeResult { lastInput = inputUrl; return result }
}

class SourceRepositoryTest {

    @Test fun addByUrlAddsResolvedFeedWithDetectedKind() = runTest {
        val dao = FakeSourceDao()
        val repo = SourceRepository(dao, FakeProber(ProbeResult.Feed("https://feeds.bbci.co.uk/news/rss.xml", "BBC", 30, "rss")), clock = { 100L })
        val r = repo.addByUrl("feeds.bbci.co.uk/news/rss.xml")
        assertTrue(r is SourceRepository.AddResult.Added)
        val added = (r as SourceRepository.AddResult.Added).source
        assertEquals("BBC", added.title)
        assertEquals(1, dao.count())
        assertEquals(1, repo.observeEnabledCount().first())
    }

    @Test fun addByUrlWithMultipleCandidatesAsksForChoice() = runTest {
        val dao = FakeSourceDao()
        val candidates = listOf(
            FeedAutodiscovery.DiscoveredFeed("https://ex.com/rss", "Main", FeedAutodiscovery.FeedType.RSS),
            FeedAutodiscovery.DiscoveredFeed("https://ex.com/comments/rss", "Comments", FeedAutodiscovery.FeedType.RSS),
        )
        val repo = SourceRepository(dao, FakeProber(ProbeResult.Candidates(candidates)))
        val r = repo.addByUrl("https://ex.com")
        assertTrue(r is SourceRepository.AddResult.NeedsChoice)
        assertEquals(2, (r as SourceRepository.AddResult.NeedsChoice).candidates.size)
        assertEquals(0, dao.count()) // nothing added until the user picks
    }

    @Test fun singleDiscoveredCandidateIsAddedDirectly() = runTest {
        val dao = FakeSourceDao()
        val one = listOf(FeedAutodiscovery.DiscoveredFeed("https://ex.com/rss", "Only", FeedAutodiscovery.FeedType.RSS))
        val repo = SourceRepository(dao, FakeProber(ProbeResult.Candidates(one)))
        val r = repo.addByUrl("https://ex.com")
        assertTrue(r is SourceRepository.AddResult.Added)
        assertEquals(1, dao.count())
    }

    @Test fun opmlRoundTripThroughRepository() = runTest {
        val dao = FakeSourceDao()
        val repo = SourceRepository(dao, FakeProber(ProbeResult.NotAFeed("n/a")), clock = { 7L })
        repo.addResolvedFeed("https://a.com/rss", "A")
        repo.addResolvedFeed("https://b.com/rss", "B")
        val xml = repo.exportOpml()
        assertTrue(xml.contains("https://a.com/rss"))

        val dao2 = FakeSourceDao()
        val repo2 = SourceRepository(dao2, FakeProber(ProbeResult.NotAFeed("n/a")), clock = { 8L })
        val n = repo2.importOpml(xml)
        assertEquals(2, n)
        assertEquals(2, dao2.count())
    }

    @Test fun enableDisableAndRemove() = runTest {
        val dao = FakeSourceDao()
        val repo = SourceRepository(dao, FakeProber(ProbeResult.NotAFeed("n/a")))
        val added = (repo.addResolvedFeed("https://a.com/rss", "A"))
        val id = added.source.id
        repo.setEnabled(id, false)
        assertEquals(0, repo.observeEnabledCount().first())
        repo.remove(id)
        assertEquals(0, dao.count())
    }
}
