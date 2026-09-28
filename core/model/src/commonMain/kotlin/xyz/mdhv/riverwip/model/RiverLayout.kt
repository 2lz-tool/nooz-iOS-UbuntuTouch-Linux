package xyz.mdhv.riverwip.model

/**
 * Pure geometry for the river visualization (brief §P4): turns a chronological
 * list of [WeeklyAggregate] rows into normalized per-topic band fractions. The
 * Compose/Canvas layer only maps these fractions to rectangles — no layout math
 * lives in Compose, so it is unit-tested without Android, including against the
 * 8+ week corpus the brief's P4 gate calls for.
 */
object RiverLayout {

    /**
     * One topic's slice of a week's column. [readFraction] is always ≤
     * [streamFraction] (reads are drawn from the stream). [streamCount]/
     * [readCount] are the raw, un-normalized item counts the fractions were
     * derived from — kept alongside them so a consumer (e.g. an accessibility
     * description) can report an absolute count without re-deriving it from
     * [WeekColumn.totalStream]/[WeekColumn.totalRead] and the fractions.
     */
    data class TopicBand(
        val topic: Topic,
        val streamFraction: Double,
        val readFraction: Double,
        val streamCount: Int,
        val readCount: Int,
    )

    data class WeekColumn(val weekStart: Long, val totalStream: Int, val totalRead: Int, val bands: List<TopicBand>)

    /**
     * Compute normalized column geometry. Each band's [TopicBand.streamFraction]
     * is that topic's count as a fraction of [maxTotal] — the largest single
     * week's stream total across the whole set — **not** of that week's own
     * total, so column heights stay visually comparable across weeks (a light
     * week reads short, a heavy week reads tall; the shape of the flow is the
     * point). Topic order is fixed ([Topic.entries]) for visual stability
     * between adjacent weeks. Weeks with zero stream are kept (as an empty
     * column) rather than dropped, so gaps in history are visible, not silently
     * skipped.
     */
    fun layout(aggregates: List<WeeklyAggregate>): List<WeekColumn> {
        if (aggregates.isEmpty()) return emptyList()
        val sorted = aggregates.sortedBy { it.weekStart }
        val maxTotal = sorted.maxOf { it.streamCountsByTopic.values.sum() }.coerceAtLeast(1)
        return sorted.map { agg ->
            val streamTotal = agg.streamCountsByTopic.values.sum()
            val readTotal = agg.readCountsByTopic.values.sum()
            val bands = Topic.entries.mapNotNull { topic ->
                val stream = agg.streamCountsByTopic[topic.key] ?: 0
                if (stream == 0) return@mapNotNull null
                val read = (agg.readCountsByTopic[topic.key] ?: 0).coerceIn(0, stream)
                TopicBand(
                    topic = topic,
                    streamFraction = stream.toDouble() / maxTotal,
                    readFraction = read.toDouble() / maxTotal,
                    streamCount = stream,
                    readCount = read,
                )
            }
            WeekColumn(weekStart = agg.weekStart, totalStream = streamTotal, totalRead = readTotal, bands = bands)
        }
    }
}
