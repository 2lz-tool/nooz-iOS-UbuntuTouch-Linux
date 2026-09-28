package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.mdhv.riverwip.data.db.ClippingDao
import xyz.mdhv.riverwip.data.db.ClippingEntity
import xyz.mdhv.riverwip.data.mapping.toClipping
import xyz.mdhv.riverwip.data.mapping.toClippingEntity
import xyz.mdhv.riverwip.model.Clipping
import xyz.mdhv.riverwip.model.Item

/**
 * The clippings shelf (owner's Clippings section). A clipping is a denormalized
 * snapshot of the article, so it survives item retention. Local only — never
 * synced or transmitted.
 */
class ClippingRepository(private val dao: ClippingDao) {

    fun observeClippings(): Flow<List<Clipping>> =
        dao.observeAll().map { list -> list.map(ClippingEntity::toClipping) }

    fun observeSavedIds(): Flow<Set<String>> =
        dao.observeSavedIds().map { it.toSet() }

    suspend fun save(item: Item, sourceTitle: String?, now: Long = nowMillis()) {
        dao.upsert(item.toClippingEntity(sourceTitle, now))
    }

    suspend fun remove(itemId: String) = dao.delete(itemId)
}
