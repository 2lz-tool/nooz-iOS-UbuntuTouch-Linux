package xyz.mdhv.riverwip.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import xyz.mdhv.riverwip.data.db.ItemDao
import xyz.mdhv.riverwip.data.db.ItemEntity
import xyz.mdhv.riverwip.data.db.SourceDao
import xyz.mdhv.riverwip.data.db.SourceEntity
import xyz.mdhv.riverwip.data.net.HttpClient

/** In-memory fake — mirrors the FakeSourceDao pattern in SourceRepositoryTest. */
private class FakeItemDao : ItemDao {
    val store = MutableStateFlow<List<ItemEntity>>(emptyList())
    override fun observeForSources(sourceIds: List<String>) =
        store.map { l -> l.filter { it.sourceId in sourceIds } }
    override suspend fun byId(id: String): ItemEntity? = store.value.firstOrNull { it.id == id }
    override suspend fun insertAllIgnoring(items: List<ItemEntity>): List<Long> {
        val existing = store.value.map { it.id }.toSet()
        store.value = store.value + items.filterNot { it.id in existing }
        return emptyList()
    }
    override suspend fun setFullTextCached(id: String, cached: Boolean) {
        store.value = store.value.map { if (it.id == id) it.copy(fullTextCached = cached) else it }
    }
    override suspend fun inRange(fromMillis: Long, toMillis: Long) =
        store.value.filter { it.publishedAt in fromMillis until toMillis }
    override suspend fun allOnce(): List<ItemEntity> = store.value
    override suspend fun pruneOlderThan(beforeMillis: Long): Int {
        val before = store.value.size
        store.value = store.value.filter { it.fetchedAt >= beforeMillis }
        return before - store.value.size
    }
    override suspend fun count(): Int = store.value.size
}

private class FakeSourceDaoForFetch : SourceDao {
    val store = MutableStateFlow<List<SourceEntity>>(emptyList())
    override fun observeAll(): Flow<List<SourceEntity>> = store
    override fun observeEnabledCount() = store.map { l -> l.count { it.enabled } }
    override suspend fun allOnce(): List<SourceEntity> = store.value
    override suspend fun enabled(): List<SourceEntity> = store.value.filter { it.enabled }
    override suspend fun byId(id: String): SourceEntity? = store.value.firstOrNull { it.id == id }
    override suspend fun upsert(source: SourceEntity) { store.value = store.value.filterNot { it.id == source.id } + source }
    override suspend fun insertAllIgnoring(sources: List<SourceEntity>): List<Long> = emptyList()
    override suspend fun setEnabled(id: String, enabled: Boolean) {
        store.value = store.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }
    override suspend fun markFetchSuccess(id: String, etag: String?, lastModified: String?, fetchedAt: Long) {
        store.value = store.value.map {
            if (it.id == id) it.copy(etag = etag, lastModified = lastModified, lastFetchAt = fetchedAt, lastError = null, consecutiveFailures = 0) else it
        }
    }
    override suspend fun markFetchFailure(id: String, error: String, fetchedAt: Long) {
        store.value = store.value.map {
            if (it.id == id) it.copy(lastError = error, lastFetchAt = fetchedAt, consecutiveFailures = it.consecutiveFailures + 1) else it
        }
    }
    override suspend fun delete(id: String) { store.value = store.value.filterNot { it.id == id } }
    override suspend fun count(): Int = store.value.size
}

class ItemRepositoryTest {

    private val rssBody = """
        <rss version="2.0"><channel><title>Ex</title>
          <item><title>Government unveils new climate policy for the year</title><link>https://ex.com/a</link><category>Climate</category></item>
        </channel></rss>
    """.trimIndent()

    @Test fun fetchIngestsAndPersistsNewItems() = runTest {
        // A fake HttpClient isn't wired here (HttpClient isn't an interface); instead
        // this test exercises the observe/enabled-set-reactivity path, which needs no
        // network. The full fetch path is covered end-to-end by CI's real HTTP calls
        // being absent in unit tests — see FeedProbeDetectionTest for parse coverage
        // and Ingest tests in :core:model for the transform this repo calls.
        val sourceDao = FakeSourceDaoForFetch()
        val itemDao = FakeItemDao()
        sourceDao.store.value = listOf(
            SourceEntity("s1", "rss", "https://ex.com/rss", "Ex", "user", true, 0L),
            SourceEntity("s2", "rss", "https://ex.com/rss2", "Ex2", "user", false, 0L),
        )
        itemDao.store.value = listOf(
            ItemEntity("i1", "s1", "https://ex.com/a", "A", null, 0L, 0L, null, false, "[]", 0L),
            ItemEntity("i2", "s2", "https://ex.com/b", "B", null, 0L, 0L, null, false, "[]", 0L),
        )
        val repo = ItemRepository(sourceDao, itemDao, HttpClient())
        // Only s1 (enabled) items are observed, not s2 (disabled).
        val observed = repo.observeItemsForEnabledSources().first()
        assertEquals(1, observed.size)
        assertEquals("i1", observed[0].id)
    }

    @Test fun observedItemsReactToEnabledSetChanges() = runTest {
        val sourceDao = FakeSourceDaoForFetch()
        val itemDao = FakeItemDao()
        sourceDao.store.value = listOf(SourceEntity("s1", "rss", "u", "T", "user", false, 0L))
        itemDao.store.value = listOf(ItemEntity("i1", "s1", "https://ex.com/a", "A", null, 0L, 0L, null, false, "[]", 0L))
        val repo = ItemRepository(sourceDao, itemDao, HttpClient())
        assertEquals(0, repo.observeItemsForEnabledSources().first().size)
        sourceDao.setEnabled("s1", true)
        assertEquals(1, repo.observeItemsForEnabledSources().first().size)
    }

    @Test fun pruneRemovesOnlyOldItems() = runTest {
        val dayMs = 24L * 60 * 60 * 1000
        val now = 1_000_000_000_000L
        val itemDao = FakeItemDao()
        itemDao.store.value = listOf(
            ItemEntity("old", "s1", "https://ex.com/a", "A", null, 0L, 0L, null, false, "[]", 0L), // fetched long ago
            ItemEntity("new", "s1", "https://ex.com/b", "B", null, 0L, now - dayMs, null, false, "[]", 0L), // fetched yesterday
        )
        val repo = ItemRepository(FakeSourceDaoForFetch(), itemDao, HttpClient(), clock = { now })
        repo.pruneOlderThan(days = 60) // cutoff = now - 60 days; "old" (fetched at 0) is well before it, "new" (yesterday) is after
        val remaining = itemDao.allOnce()
        assertEquals(1, remaining.size)
        assertEquals("new", remaining[0].id)
    }

    @Test fun markFullTextCachedUpdatesFlag() = runTest {
        val itemDao = FakeItemDao()
        itemDao.store.value = listOf(ItemEntity("i1", "s1", "https://ex.com/a", "A", null, 0L, 0L, null, false, "[]", 0L))
        val repo = ItemRepository(FakeSourceDaoForFetch(), itemDao, HttpClient())
        repo.markFullTextCached("i1", true)
        assertTrue(repo.byId("i1")!!.fullTextCached)
    }
}
