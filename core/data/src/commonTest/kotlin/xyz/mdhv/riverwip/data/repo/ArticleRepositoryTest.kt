package xyz.mdhv.riverwip.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import xyz.mdhv.riverwip.data.cache.FullTextCache
import xyz.mdhv.riverwip.data.db.ArticleTextDao
import xyz.mdhv.riverwip.data.db.ArticleTextEntity
import xyz.mdhv.riverwip.data.db.ArticleTextHit
import xyz.mdhv.riverwip.data.db.ItemDao
import xyz.mdhv.riverwip.data.db.ItemEntity
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.data.TEST_DIR
import xyz.mdhv.riverwip.data.newFakeFileSystem

private class FakeItemDaoForArticles : ItemDao {
    val store = MutableStateFlow<List<ItemEntity>>(emptyList())
    override fun observeForSources(sourceIds: List<String>): Flow<List<ItemEntity>> = throw NotImplementedError()
    override suspend fun byId(id: String): ItemEntity? = store.value.firstOrNull { it.id == id }
    override suspend fun insertAllIgnoring(items: List<ItemEntity>): List<Long> = emptyList()
    override suspend fun setFullTextCached(id: String, cached: Boolean) {
        store.value = store.value.map { if (it.id == id) it.copy(fullTextCached = cached) else it }
    }
    override suspend fun inRange(fromMillis: Long, toMillis: Long): List<ItemEntity> = emptyList()
    override suspend fun allOnce(): List<ItemEntity> = store.value
    override suspend fun pruneOlderThan(beforeMillis: Long): Int = 0
    override suspend fun count(): Int = store.value.size
}

/**
 * In-memory stand-in for the FTS index. `search` is a substring scan rather
 * than real FTS — the actual MATCH semantics are pinned against real SQLite in
 * RiverDatabaseMigrationTest; what this fake exists to check is that the
 * repository *keeps the index in step* with the cache.
 */
private class FakeArticleTextDao : ArticleTextDao {
    val rows = mutableListOf<ArticleTextEntity>()
    override suspend fun insert(entity: ArticleTextEntity) { rows.add(entity) }
    override suspend fun deleteFor(itemId: String) { rows.removeAll { it.itemId == itemId } }
    override suspend fun search(match: String, limit: Int): List<ArticleTextHit> =
        rows.filter { it.body.contains(match.trim('*'), ignoreCase = true) }
            .take(limit)
            .map { ArticleTextHit(it.itemId, it.body) }
    override suspend fun isIndexed(itemId: String): Boolean = rows.any { it.itemId == itemId }
    override suspend fun indexedItemIds(): List<String> = rows.map { it.itemId }
    override suspend fun count(): Int = rows.size
    override suspend fun pruneOrphans(): Int = 0
}

private class FakeArticleFetcher(private val response: HttpClient.Response? = null, private val throwOn: Exception? = null) : ArticleFetcher {
    override suspend fun fetch(url: String): HttpClient.Response {
        throwOn?.let { throw it }
        return response!!
    }
}

private fun fakeResponse(code: Int, body: String) =
    HttpClient.Response(code = code, body = body, etag = null, lastModified = null, contentType = "text/html", finalUrl = "https://ex.com/a")

class ArticleRepositoryTest {

    private val fs = newFakeFileSystem()
    private val dir = TEST_DIR

    private val articleHtml = """
        <html><body><article>
          <h1>Test Story</h1>
          <p>This is the first substantial paragraph of the article, long enough to survive the extraction threshold easily.</p>
          <p>This is the second substantial paragraph, continuing the article body content for the extraction test to verify.</p>
        </article></body></html>
    """.trimIndent()

    @Test fun cacheHitSkipsFetch() = runTest {
        val cache = FullTextCache(fs, dir, maxBytes = 10_000_000)
        cache.put("i1", "cached paragraph one\n\ncached paragraph two")
        val itemDao = FakeItemDaoForArticles()
        val repo = ArticleRepository(itemDao, FakeArticleFetcher(throwOn = IllegalStateException("should not fetch")), cache, FakeArticleTextDao())
        val result = repo.textFor("i1", "https://ex.com/a")
        assertTrue(result!!.fromCache)
        assertEquals(2, result.paragraphs.size)
    }

    @Test fun fetchExtractAndCacheOnMiss() = runTest {
        val cache = FullTextCache(fs, dir, maxBytes = 10_000_000)
        val itemDao = FakeItemDaoForArticles()
        itemDao.store.value = listOf(ItemEntity("i1", "s1", "https://ex.com/a", "Test Story", null, 0L, 0L, null, false, "[]", 0L))
        val repo = ArticleRepository(itemDao, FakeArticleFetcher(fakeResponse(200, articleHtml)), cache, FakeArticleTextDao())
        val result = repo.textFor("i1", "https://ex.com/a")
        assertTrue(result != null && !result.fromCache)
        assertEquals(2, result!!.paragraphs.size)
        // Cached for next time, and the item's fullTextCached flag flipped.
        assertTrue(cache.contains("i1"))
        assertTrue(itemDao.byId("i1")!!.fullTextCached)
    }

    @Test fun httpFailureReturnsNullAndDoesNotCache() = runTest {
        val cache = FullTextCache(fs, dir, maxBytes = 10_000_000)
        val repo = ArticleRepository(FakeItemDaoForArticles(), FakeArticleFetcher(fakeResponse(404, "not found")), cache, FakeArticleTextDao())
        assertNull(repo.textFor("i1", "https://ex.com/a"))
        assertTrue(!cache.contains("i1"))
    }

    @Test fun networkExceptionReturnsNull() = runTest {
        val cache = FullTextCache(fs, dir, maxBytes = 10_000_000)
        val repo = ArticleRepository(FakeItemDaoForArticles(), FakeArticleFetcher(throwOn = okio.IOException("timeout")), cache, FakeArticleTextDao())
        assertNull(repo.textFor("i1", "https://ex.com/a"))
    }

    @Test fun extractionYieldingNothingReturnsNull() = runTest {
        val cache = FullTextCache(fs, dir, maxBytes = 10_000_000)
        val repo = ArticleRepository(FakeItemDaoForArticles(), FakeArticleFetcher(fakeResponse(200, "<html><body>too short</body></html>")), cache, FakeArticleTextDao())
        assertNull(repo.textFor("i1", "https://ex.com/a"))
    }

    @Test fun extractingAnArticleAlsoIndexesItForSearch() = runTest {
        val cache = FullTextCache(fs, dir, maxBytes = 10_000_000)
        val textDao = FakeArticleTextDao()
        val repo = ArticleRepository(FakeItemDaoForArticles(), FakeArticleFetcher(fakeResponse(200, articleHtml)), cache, textDao)

        repo.textFor("i1", "https://ex.com/a")

        assertEquals(listOf("i1"), textDao.indexedItemIds())
        assertTrue(textDao.rows.single().body.contains("first substantial paragraph"), "the indexed body is the extracted prose")
    }

    @Test fun openingAnArticleCachedBeforeTheIndexExistedBackfillsIt() = runTest {
        // The existing 200MB cache predates the index and has no batch
        // migration; opening an article is what pulls it in.
        val cache = FullTextCache(fs, dir, maxBytes = 10_000_000)
        cache.put("old", "a paragraph about the monsoon")
        val textDao = FakeArticleTextDao()
        val repo = ArticleRepository(FakeItemDaoForArticles(), FakeArticleFetcher(throwOn = IllegalStateException("should not fetch")), cache, textDao)

        assertEquals(0, textDao.count())
        repo.textFor("old", "https://ex.com/a")
        assertEquals(listOf("old"), textDao.indexedItemIds())

        // ...and opening it again does not index a second copy.
        repo.textFor("old", "https://ex.com/a")
        assertEquals(1, textDao.count())
    }

}
