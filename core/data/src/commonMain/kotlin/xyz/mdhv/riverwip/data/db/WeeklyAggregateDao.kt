package xyz.mdhv.riverwip.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyAggregateDao {

    /** Upserted by weekStart (the primary key) — recomputation is naturally idempotent. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(aggregate: WeeklyAggregateEntity)

    @Query("SELECT * FROM weekly_aggregates ORDER BY weekStart ASC")
    fun observeAll(): Flow<List<WeeklyAggregateEntity>>

    @Query("SELECT * FROM weekly_aggregates ORDER BY weekStart ASC")
    suspend fun allOnce(): List<WeeklyAggregateEntity>

    @Query("SELECT COUNT(*) FROM weekly_aggregates")
    suspend fun count(): Int
}
