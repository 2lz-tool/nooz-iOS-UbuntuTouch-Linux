package xyz.mdhv.riverwip.model

import kotlin.random.Random

/**
 * Synthetic weekly corpus generator (brief §8: "configurable topic mixes over
 * weeks, for river development"). Deterministic given a seed, so generated
 * fixtures are reproducible in previews and tests. Read counts are always drawn
 * as a subset of that week's stream count, preserving the "reads are drawn from
 * the stream" invariant [RiverAnalysis.decompose] relies on for exactness.
 */
object SyntheticCorpus {

    /** One week's shape: how much of each topic flowed, and roughly what fraction the user read. */
    data class WeekSpec(
        val streamByTopic: Map<Topic, Int>,
        val readFractionByTopic: Map<Topic, Double> = emptyMap(),
        val defaultReadFraction: Double = 0.05,
    )

    /** Generate [WeeklyAggregate] rows for consecutive periods starting at [startPeriod]. */
    fun generate(
        weeks: List<WeekSpec>,
        periodDays: Int = WeekBucketing.DEFAULT_PERIOD_DAYS,
        startPeriod: Long = 0L,
        seed: Long = 42L,
    ): List<WeeklyAggregate> {
        val rng = Random(seed)
        val periodMillis = periodDays.toLong() * 24 * 60 * 60 * 1000
        return weeks.mapIndexed { i, spec ->
            val weekStart = startPeriod + i * periodMillis
            val streamCounts = spec.streamByTopic.mapKeys { it.key.key }
            val readCounts = HashMap<String, Int>()
            for ((topic, stream) in spec.streamByTopic) {
                val frac = (spec.readFractionByTopic[topic] ?: spec.defaultReadFraction).coerceIn(0.0, 1.0)
                val read = (0 until stream).count { rng.nextDouble() < frac }
                readCounts[topic.key] = read
            }
            val sourceCounts = mapOf("synthetic" to spec.streamByTopic.values.sum())
            WeeklyAggregate(weekStart, streamCounts, readCounts, sourceCounts)
        }
    }

    /** A ready-made mixed corpus for dev/preview screens (brief §P4 gate: renders 8+ weeks). */
    fun defaultPreview(weekCount: Int = 10, seed: Long = 42L): List<WeeklyAggregate> {
        val rng = Random(seed)
        val topics = Topic.entries.filter { it != Topic.OTHER }
        val weeks = (0 until weekCount).map {
            val mix = topics.associateWith { 20 + rng.nextInt(200) }
            WeekSpec(streamByTopic = mix, defaultReadFraction = 0.03 + rng.nextDouble() * 0.15)
        }
        return generate(weeks, seed = seed)
    }
}
