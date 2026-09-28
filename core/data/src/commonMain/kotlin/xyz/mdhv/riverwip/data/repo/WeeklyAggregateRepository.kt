package xyz.mdhv.riverwip.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.mdhv.riverwip.data.db.ItemDao
import xyz.mdhv.riverwip.data.db.ReadEventDao
import xyz.mdhv.riverwip.data.db.WeeklyAggregateDao
import xyz.mdhv.riverwip.data.mapping.toDomain
import xyz.mdhv.riverwip.data.mapping.toEntity
import xyz.mdhv.riverwip.model.WeeklyAggregate
import xyz.mdhv.riverwip.model.WeekBucketing
import xyz.mdhv.riverwip.model.WeeklyAggregator

/**
 * The river's data source (brief §P4): the tiny, permanent [WeeklyAggregate]
 * rows. [recompute] rolls the current Item/ReadEvent rows up into aggregates
 * after each fetch cycle — the aggregation *rule* itself lives in
 * `:core:model`'s [WeeklyAggregator] and is unit-tested there without Room.
 */
class WeeklyAggregateRepository(
    private val itemDao: ItemDao,
    private val readEventDao: ReadEventDao,
    private val weeklyAggregateDao: WeeklyAggregateDao,
) {
    fun observeAggregates(): Flow<List<WeeklyAggregate>> =
        weeklyAggregateDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun aggregatesOnce(): List<WeeklyAggregate> =
        weeklyAggregateDao.allOnce().map { it.toDomain() }

    /** Recompute every period's aggregate from raw rows and upsert. Call after each fetch cycle. */
    suspend fun recompute(periodDays: Int = WeekBucketing.DEFAULT_PERIOD_DAYS) {
        val items = itemDao.allOnce().map { it.toDomain() }
        val reads = readEventDao.allOnce().map { it.toDomain() }
        val aggregates = WeeklyAggregator.aggregate(items, reads, periodDays)
        for (agg in aggregates) weeklyAggregateDao.upsert(agg.toEntity())
    }

    /**
     * Day-grained aggregates for the loom (owner's day-loom flow), computed on
     * demand from raw rows (period is a parameter per the brief — the persisted
     * roll-up stays weekly). Bounded by Item retention (~60 days).
     */
    suspend fun dailyAggregates(): List<WeeklyAggregate> {
        val items = itemDao.allOnce().map { it.toDomain() }
        val reads = readEventDao.allOnce().map { it.toDomain() }
        return WeeklyAggregator.aggregate(items, reads, periodDays = 1)
    }
}
