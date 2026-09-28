package xyz.mdhv.riverwip.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE sourceId IN (:sourceIds) ORDER BY publishedAt DESC")
    fun observeForSources(sourceIds: List<String>): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun byId(id: String): ItemEntity?

    /** Conflict-ignore relies on [id] being content-derived from the canonical URL — a re-fetch of the same article naturally no-ops. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoring(items: List<ItemEntity>): List<Long>

    @Query("UPDATE items SET fullTextCached = :cached WHERE id = :id")
    suspend fun setFullTextCached(id: String, cached: Boolean)

    @Query("SELECT * FROM items WHERE publishedAt >= :fromMillis AND publishedAt < :toMillis")
    suspend fun inRange(fromMillis: Long, toMillis: Long): List<ItemEntity>

    /** Full snapshot for aggregation (brief §P4). Retention keeps this bounded (60-day default prune). */
    @Query("SELECT * FROM items")
    suspend fun allOnce(): List<ItemEntity>

    /** Retention: prune rows older than the cutoff (brief §4, default 60 days) — aggregates survive independently. */
    @Query("DELETE FROM items WHERE fetchedAt < :beforeMillis")
    suspend fun pruneOlderThan(beforeMillis: Long): Int

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int
}
