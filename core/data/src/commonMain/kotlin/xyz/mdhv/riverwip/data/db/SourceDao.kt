package xyz.mdhv.riverwip.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SourceEntity>>

    /** The honest denominator: the count of the user's *enabled* sources (brief §1). */
    @Query("SELECT COUNT(*) FROM sources WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Query("SELECT * FROM sources ORDER BY addedAt DESC")
    suspend fun allOnce(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE enabled = 1")
    suspend fun enabled(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun byId(id: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: SourceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoring(sources: List<SourceEntity>): List<Long>

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query(
        """
        UPDATE sources SET etag = :etag, lastModified = :lastModified,
            lastFetchAt = :fetchedAt, lastError = NULL, consecutiveFailures = 0
        WHERE id = :id
        """,
    )
    suspend fun markFetchSuccess(id: String, etag: String?, lastModified: String?, fetchedAt: Long)

    @Query(
        """
        UPDATE sources SET lastError = :error, lastFetchAt = :fetchedAt,
            consecutiveFailures = consecutiveFailures + 1
        WHERE id = :id
        """,
    )
    suspend fun markFetchFailure(id: String, error: String, fetchedAt: Long)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int
}
