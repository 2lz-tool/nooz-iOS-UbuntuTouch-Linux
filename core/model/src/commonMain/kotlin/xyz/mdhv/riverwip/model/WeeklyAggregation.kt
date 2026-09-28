package xyz.mdhv.riverwip.model

/**
 * Pure roll-up of [Item]/[ReadEvent] rows into the tiny, permanent
 * [WeeklyAggregate] the river renders (brief §P4). Lives in `:core:model` so the
 * aggregation rule is unit-tested without Room/Android; `:core:data` only loads
 * rows and calls this.
 */
object WeeklyAggregator {

    /**
     * Aggregate [items] (stream) and [readEvents] (intake) into one
     * [WeeklyAggregate] per period they touch. A read event whose item isn't in
     * [items] is skipped (its source may since have been disabled/removed —
     * counting it would violate the "reads are drawn from the stream" invariant
     * that [RiverAnalysis.decompose] relies on for exactness).
     */
    fun aggregate(
        items: List<Item>,
        readEvents: List<ReadEvent>,
        periodDays: Int = WeekBucketing.DEFAULT_PERIOD_DAYS,
    ): List<WeeklyAggregate> {
        val itemsById = items.associateBy { it.id }
        val streamByPeriod = HashMap<Long, MutableMap<String, Int>>()
        val sourceByPeriod = HashMap<Long, MutableMap<String, Int>>()
        for (item in items) {
            val period = WeekBucketing.periodStart(item.publishedAt, periodDays)
            val topic = Classifier.dominantTopic(item.topics)
            streamByPeriod.getOrPut(period) { HashMap() }.merge(topic.key, 1, Int::plus)
            sourceByPeriod.getOrPut(period) { HashMap() }.merge(item.sourceId, 1, Int::plus)
        }
        val readByPeriod = HashMap<Long, MutableMap<String, Int>>()
        for (ev in readEvents) {
            val item = itemsById[ev.itemId] ?: continue
            val period = WeekBucketing.periodStart(ev.openedAt, periodDays)
            val topic = Classifier.dominantTopic(item.topics)
            readByPeriod.getOrPut(period) { HashMap() }.merge(topic.key, 1, Int::plus)
        }
        val periods = (streamByPeriod.keys + readByPeriod.keys).toSortedSet()
        return periods.map { p ->
            WeeklyAggregate(
                weekStart = p,
                streamCountsByTopic = streamByPeriod[p].orEmpty(),
                readCountsByTopic = readByPeriod[p].orEmpty(),
                sourceCounts = sourceByPeriod[p].orEmpty(),
            )
        }
    }
}

private fun Map<String, Int>.toTopicKeyed(): Map<Topic, Int> {
    val out = HashMap<Topic, Int>()
    for ((k, v) in this) out.merge(Topic.fromKey(k), v, Int::plus)
    return out
}

/** [WeeklyAggregate.streamCountsByTopic] with String keys resolved to [Topic] — for feeding [RiverAnalysis]. */
fun WeeklyAggregate.streamByTopic(): Map<Topic, Int> = streamCountsByTopic.toTopicKeyed()

/** [WeeklyAggregate.readCountsByTopic] with String keys resolved to [Topic] — for feeding [RiverAnalysis]. */
fun WeeklyAggregate.readByTopic(): Map<Topic, Int> = readCountsByTopic.toTopicKeyed()
