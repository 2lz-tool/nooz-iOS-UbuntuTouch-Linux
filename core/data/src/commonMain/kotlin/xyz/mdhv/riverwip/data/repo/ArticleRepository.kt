package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.cache.FullTextCache
import xyz.mdhv.riverwip.data.db.ArticleTextDao
import xyz.mdhv.riverwip.data.db.ArticleTextEntity
import xyz.mdhv.riverwip.data.db.ArticleTextHit
import xyz.mdhv.riverwip.data.db.ItemDao
import xyz.mdhv.riverwip.data.extract.ArticleExtractor
import xyz.mdhv.riverwip.data.net.HttpClient

/** Abstraction over fetching an article's raw HTML, so [ArticleRepository] is unit-testable with a fake. */
interface ArticleFetcher {
    suspend fun fetch(url: String): HttpClient.Response
}

class HttpArticleFetcher(private val http: HttpClient) : ArticleFetcher {
    override suspend fun fetch(url: String): HttpClient.Response = http.get(url)
}

/**
 * Full-text extraction + offline cache (brief §P3). Feeds often truncate; this
 * fetches the original article, extracts its body with [ArticleExtractor], and
 * caches the result so it reads offline afterward.
 *
 * It is also where the Stand's search index is kept in step (D37): every body
 * that lands in the cache is mirrored into the `article_text` FTS table, since
 * this is the one place article prose exists at all.
 */
class ArticleRepository(
    private val itemDao: ItemDao,
    private val fetcher: ArticleFetcher,
    private val cache: FullTextCache,
    private val articleTextDao: ArticleTextDao,
) {
    data class ArticleText(val paragraphs: List<String>, val fromCache: Boolean)

    /** Cached text if present; otherwise fetch + extract + cache. Null if extraction fails or yields nothing. */
    suspend fun textFor(itemId: String, url: String): ArticleText? {
        cache.get(itemId)?.let { cached ->
            // Opening an article cached before the index existed is what
            // backfills it. There is no batch migration for the existing cache
            // — up to 200MB of it — and this costs one EXISTS query per open
            // while making the search quietly better the more the reader reads.
            if (!articleTextDao.isIndexed(itemId)) index(itemId, cached)
            return ArticleText(cached.split(PARAGRAPH_SEP), fromCache = true)
        }
        val resp = try {
            fetcher.fetch(url)
        } catch (_: Exception) {
            return null
        }
        if (!resp.isSuccess) return null
        val extracted = ArticleExtractor.extract(resp.body, baseUri = url)
        if (!extracted.isUsable) return null
        val body = extracted.paragraphs.joinToString(PARAGRAPH_SEP)
        cache.put(itemId, body)
        itemDao.setFullTextCached(itemId, true)
        index(itemId, body)
        return ArticleText(extracted.paragraphs, fromCache = false)
    }

    /**
     * Bodies matching an FTS expression from `ArticleSearch.toMatchQuery`.
     * Bounded: the caller only needs enough text to cut a snippet from, and an
     * unbounded MATCH over a year of reading returns a great deal of prose.
     */
    suspend fun searchBodies(match: String, limit: Int = SEARCH_LIMIT): List<ArticleTextHit> =
        articleTextDao.search(match, limit)

    /**
     * Drops index rows for articles that no longer exist. Items are pruned on a
     * ~60-day retention; without this the index would outlive them, holding
     * prose for stories the reader can no longer open and growing without any
     * bound of its own.
     */
    suspend fun pruneIndexOrphans(): Int = articleTextDao.pruneOrphans()

    suspend fun indexedArticleCount(): Int = articleTextDao.count()

    fun currentCacheSizeBytes(): Long = cache.currentSizeBytes()

    /**
     * Evicts an article's cached text *and* its index row. The index holds a
     * second copy of the same prose, so dropping only the cache would free
     * roughly half of what the caller asked to free.
     */
    suspend fun evictItem(itemId: String) {
        cache.remove(itemId)
        articleTextDao.deleteFor(itemId)
    }

    /** FTS4's only key is its implicit rowid, so re-indexing means delete-then-insert. */
    private suspend fun index(itemId: String, body: String) {
        articleTextDao.deleteFor(itemId)
        articleTextDao.insert(ArticleTextEntity(itemId = itemId, body = body))
    }

    companion object {
        private const val PARAGRAPH_SEP = "\n\n"
        private const val SEARCH_LIMIT = 40
    }
}
