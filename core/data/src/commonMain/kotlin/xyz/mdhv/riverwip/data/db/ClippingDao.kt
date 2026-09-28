package xyz.mdhv.riverwip.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClippingDao {

    @Query("SELECT * FROM clippings ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<ClippingEntity>>

    /** Just the ids, so the reader can show a filled/empty bookmark without loading every clipping. */
    @Query("SELECT itemId FROM clippings")
    fun observeSavedIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(clipping: ClippingEntity)

    @Query("DELETE FROM clippings WHERE itemId = :itemId")
    suspend fun delete(itemId: String)
}
