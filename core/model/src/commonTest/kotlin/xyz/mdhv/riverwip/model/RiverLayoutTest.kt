package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiverLayoutTest {

    private fun agg(week: Long, stream: Map<Topic, Int>, read: Map<Topic, Int>) = WeeklyAggregate(
        weekStart = week,
        streamCountsByTopic = stream.mapKeys { it.key.key },
        readCountsByTopic = read.mapKeys { it.key.key },
        sourceCounts = emptyMap(),
    )

    @Test fun emptyInputYieldsEmptyLayout() {
        assertTrue(RiverLayout.layout(emptyList()).isEmpty())
    }

    @Test fun columnsSortedChronologically() {
        val out = RiverLayout.layout(
            listOf(
                agg(2000L, mapOf(Topic.SPORT to 10), mapOf(Topic.SPORT to 1)),
                agg(1000L, mapOf(Topic.SPORT to 10), mapOf(Topic.SPORT to 1)),
            ),
        )
        assertEquals(listOf(1000L, 2000L), out.map { it.weekStart })
    }

    @Test fun readFractionNeverExceedsStreamFraction() {
        // Deliberately malformed input (read > stream, shouldn't happen given the
        // domain invariant, but the layout must clamp defensively rather than
        // draw a nonsensical band).
        val out = RiverLayout.layout(listOf(agg(0L, mapOf(Topic.TECH to 5), mapOf(Topic.TECH to 99))))
        val band = out[0].bands.first { it.topic == Topic.TECH }
        assertTrue(band.readFraction <= band.streamFraction)
    }

    @Test fun rawCountsAreAbsoluteNotNormalized() {
        // streamCount/readCount must survive as the actual item counts (clamped,
        // for read) regardless of how the fractions get scaled against maxTotal —
        // an accessibility description reads these, not the fractions.
        val out = RiverLayout.layout(
            listOf(
                agg(0L, mapOf(Topic.TECH to 100), mapOf(Topic.TECH to 10)),
                agg(1L, mapOf(Topic.TECH to 5), mapOf(Topic.TECH to 99)),
            ),
        )
        val heavy = out[0].bands.first { it.topic == Topic.TECH }
        assertEquals(100, heavy.streamCount)
        assertEquals(10, heavy.readCount)
        val clamped = out[1].bands.first { it.topic == Topic.TECH }
        assertEquals(5, clamped.streamCount)
        assertEquals(5, clamped.readCount) // clamped to streamCount, same as readFraction
    }

    @Test fun heaviestWeekReachesFullScaleOthersAreProportional() {
        val out = RiverLayout.layout(
            listOf(
                agg(0L, mapOf(Topic.SPORT to 100), mapOf(Topic.SPORT to 10)),
                agg(1L, mapOf(Topic.SPORT to 50), mapOf(Topic.SPORT to 5)),
            ),
        )
        val heavy = out[0].bands.first { it.topic == Topic.SPORT }
        val light = out[1].bands.first { it.topic == Topic.SPORT }
        assertEquals(1.0, heavy.streamFraction, 1e-9) // scaled against itself as maxTotal
        assertEquals(0.5, light.streamFraction, 1e-9) // half the heaviest week's total
    }

    @Test fun zeroCountTopicsAreOmittedNotZeroHeightBands() {
        val out = RiverLayout.layout(listOf(agg(0L, mapOf(Topic.SPORT to 10, Topic.TECH to 0), emptyMap())))
        assertEquals(1, out[0].bands.size)
        assertEquals(Topic.SPORT, out[0].bands[0].topic)
    }

    @Test fun emptyWeekIsKeptAsAnEmptyColumnNotDropped() {
        val out = RiverLayout.layout(
            listOf(
                agg(0L, mapOf(Topic.SPORT to 10), mapOf(Topic.SPORT to 1)),
                agg(1L, emptyMap(), emptyMap()),
            ),
        )
        assertEquals(2, out.size)
        assertTrue(out[1].bands.isEmpty())
    }

    @Test fun gateEightPlusWeeksOfSyntheticCorpusProducesSaneGeometry() {
        // Proxy for the P4 gate ("renders 8+ weeks... smoothly") at the layout
        // level: every fraction is finite, in [0,1], and read <= stream.
        val corpus = SyntheticCorpus.defaultPreview(weekCount = 12, seed = 3L)
        val out = RiverLayout.layout(corpus)
        assertEquals(12, out.size)
        for (col in out) {
            for (band in col.bands) {
                assertTrue(band.streamFraction in 0.0..1.0)
                assertTrue(band.readFraction in 0.0..1.0)
                assertTrue(band.readFraction <= band.streamFraction)
                assertTrue(band.streamFraction.isFinite() && band.readFraction.isFinite())
            }
        }
    }

    @Test fun mixedSyntheticAndRealShapedWeeksLayOutTogether() {
        // "Mixed synthetic + real data" per the P4 gate: concatenate a synthetic
        // run with a couple of hand-authored "real-shaped" aggregates and verify
        // the combined layout is still well-formed.
        val synthetic = SyntheticCorpus.defaultPreview(weekCount = 8, seed = 11L)
        val real = listOf(
            agg(synthetic.last().weekStart + WeekBucketing.DEFAULT_PERIOD_DAYS * 24L * 60 * 60 * 1000, mapOf(Topic.POLITICS to 412, Topic.SPORT to 88), mapOf(Topic.POLITICS to 9, Topic.SPORT to 20)),
        )
        val out = RiverLayout.layout(synthetic + real)
        assertEquals(9, out.size)
        assertTrue(out.zipWithNext().all { (a, b) -> a.weekStart < b.weekStart })
    }
}
