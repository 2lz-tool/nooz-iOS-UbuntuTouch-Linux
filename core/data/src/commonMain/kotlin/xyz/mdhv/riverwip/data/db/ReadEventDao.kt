package xyz.mdhv.riverwip.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadEventDao {

    @Insert
    suspend fun insert(event: ReadEventEntity): Long

    @Query("SELECT * FROM read_events WHERE openedAt >= :fromMillis AND openedAt < :toMillis")
    suspend fun inRange(fromMillis: Long, toMillis: Long): List<ReadEventEntity>

    /** Full snapshot for aggregation (brief §P4); read events are small (coarse buckets only). */
    @Query("SELECT * FROM read_events")
    suspend fun allOnce(): List<ReadEventEntity>

    /** Live version of [allOnce], for surfaces that must reflect a read the moment it's recorded (the Stand's day-mix bar). */
    @Query("SELECT * FROM read_events")
    fun observeAll(): Flow<List<ReadEventEntity>>

    @Query("SELECT COUNT(*) FROM read_events WHERE itemId = :itemId")
    suspend fun countForItem(itemId: String): Int
}
