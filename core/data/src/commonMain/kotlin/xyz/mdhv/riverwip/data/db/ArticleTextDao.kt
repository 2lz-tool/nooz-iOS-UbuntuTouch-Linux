package xyz.mdhv.riverwip.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** One FTS hit: which article, and its body so a snippet can be cut from it. */
data class ArticleTextHit(val itemId: String, val body: String)

/**
 * Full-text access to the bodies of articles the reader has opened (D37).
 *
 * Every query here is bounded or scoped: an FTS `MATCH` over a reader's whole
 * reading history can return a lot of prose, and the caller only ever needs
 * enough of it to cut a snippet from.
 */
@Dao
interface ArticleTextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ArticleTextEntity)

    /**
     * Removes any existing row for an article before it is re-indexed. FTS4 has
     * no unique constraint on `itemId` — its only key is the implicit rowid —
     * so without this, re-extracting an article would index it a second time
     * and it would appear twice in results.
     */
    @Query("DELETE FROM article_text WHERE itemId = :itemId")
    suspend fun deleteFor(itemId: String)

    /**
     * Bodies matching an FTS expression built by `ArticleSearch.toMatchQuery`.
     * Never pass a raw user string here — it is FTS syntax, not a literal.
     */
    @Query("SELECT itemId, body FROM article_text WHERE article_text MATCH :match LIMIT :limit")
    suspend fun search(match: String, limit: Int): List<ArticleTextHit>

    @Query("SELECT EXISTS(SELECT 1 FROM article_text WHERE itemId = :itemId)")
    suspend fun isIndexed(itemId: String): Boolean

    @Query("SELECT itemId FROM article_text")
    suspend fun indexedItemIds(): List<String>

    @Query("SELECT COUNT(*) FROM article_text")
    suspend fun count(): Int

    /**
     * Drops index rows whose article is gone. Items are pruned on a ~60-day
     * retention; without this the index would outlive them and grow without
     * bound, holding prose for stories the reader can no longer open.
     */
    @Query("DELETE FROM article_text WHERE itemId NOT IN (SELECT id FROM items)")
    suspend fun pruneOrphans(): Int
}
