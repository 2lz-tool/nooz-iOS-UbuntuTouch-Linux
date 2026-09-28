package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyAggregationTest {

    private fun item(id: String, sourceId: String, publishedAt: Long, topic: Topic) = Item(
        id = id, sourceId = sourceId, canonicalUrl = "https://ex.com/$id", title = "t",
        publishedAt = publishedAt, fetchedAt = publishedAt,
        topics = listOf(TopicEvidence(topic, "lexicon:${topic.key}", listOf("x"))),
    )

    @Test fun periodStartIsMondayUtcAndStable() {
        // 2024-10-02 is a Wednesday; the ISO week's Monday is 2024-09-30.
        val wed = java.time.LocalDate.of(2024, 10, 2).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val monday = java.time.LocalDate.of(2024, 9, 30).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(monday, WeekBucketing.periodStart(wed))
        // Every timestamp within the week maps to the same start.
        val fri = wed + 2L * 24 * 60 * 60 * 1000
        assertEquals(monday, WeekBucketing.periodStart(fri))
    }

    @Test fun aggregatesStreamAndReadCountsByDominantTopic() {
        val p0 = 0L
        val items = listOf(
            item("a", "s1", p0, Topic.SPORT),
            item("b", "s1", p0 + 1000, Topic.SPORT),
            item("c", "s1", p0 + 2000, Topic.POLITICS),
        )
        val reads = listOf(ReadEvent("a", p0 + 500, DwellBucket.READ, viaRiver = true))
        val agg = WeeklyAggregator.aggregate(items, reads)
        assertEquals(1, agg.size)
        val a = agg[0]
        assertEquals(2, a.streamCountsByTopic["sport"])
        assertEquals(1, a.streamCountsByTopic["politics"])
        assertEquals(1, a.readCountsByTopic["sport"])
        assertEquals(3, a.sourceCounts["s1"])
    }

    @Test fun readEventForUnknownItemIsSkipped() {
        val items = listOf(item("a", "s1", 0L, Topic.TECH))
        val reads = listOf(ReadEvent("missing", 100L, DwellBucket.GLANCE, viaRiver = false))
        val agg = WeeklyAggregator.aggregate(items, reads)
        assertEquals(0, agg[0].readCountsByTopic.values.sum())
    }

    @Test fun topicKeyedExtensionsResolveBackToTopics() {
        val agg = WeeklyAggregate(0L, mapOf("sport" to 5, "unknownkey" to 2), mapOf("sport" to 1), emptyMap())
        val stream = agg.streamByTopic()
        assertEquals(5, stream[Topic.SPORT])
        assertEquals(2, stream[Topic.OTHER]) // unknown key falls back to OTHER
    }

    @Test fun syntheticCorpusIsDeterministicAndRespectsReadLeStreamInvariant() {
        val a = SyntheticCorpus.defaultPreview(weekCount = 8, seed = 7L)
        val b = SyntheticCorpus.defaultPreview(weekCount = 8, seed = 7L)
        assertEquals(a, b) // same seed -> identical corpus
        assertEquals(8, a.size)
        for (week in a) {
            for ((topicKey, streamCount) in week.streamCountsByTopic) {
                val readCount = week.readCountsByTopic[topicKey] ?: 0
                assertTrue("read<=stream for $topicKey", readCount <= streamCount)
            }
        }
    }

    @Test fun syntheticCorpusFeedsDecompositionExactly() {
        val weeks = SyntheticCorpus.defaultPreview(weekCount = 8, seed = 99L)
        for (i in 1 until weeks.size) {
            val d = RiverAnalysis.decompose(
                weeks[i - 1].streamByTopic(), weeks[i - 1].readByTopic(),
                weeks[i].streamByTopic(), weeks[i].readByTopic(),
            )
            for (t in d) assertTrue(kotlin.math.abs(t.residual) < 1e-9)
        }
    }
}
