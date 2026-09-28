package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RiverAnalysisTest {

    private fun m(vararg pairs: Pair<Topic, Int>) = mapOf(*pairs)

    @Test fun coverageIsReadOverFlowed() {
        val stream = m(Topic.POLITICS to 300, Topic.SPORT to 100)
        val read = m(Topic.POLITICS to 9, Topic.SPORT to 11)
        assertEquals(20.0 / 400.0, RiverAnalysis.coverage(stream, read), 1e-12)
    }

    @Test fun breadthOfEvenSplitIsTopicCount() {
        val even = m(Topic.POLITICS to 10, Topic.SPORT to 10, Topic.TECH to 10, Topic.HEALTH to 10)
        assertEquals(4.0, RiverAnalysis.breadth(even), 1e-9)
        val single = m(Topic.POLITICS to 42)
        assertEquals(1.0, RiverAnalysis.breadth(single), 1e-9)
    }

    @Test fun overUnderAboveOneMeansOverRead() {
        val stream = m(Topic.POLITICS to 50, Topic.SPORT to 50) // 50/50 supply
        val read = m(Topic.POLITICS to 90, Topic.SPORT to 10)   // 90/10 intake
        val ou = RiverAnalysis.overUnderRatio(stream, read)
        assertTrue(ou.getValue(Topic.POLITICS) > 1.0)
        assertTrue(ou.getValue(Topic.SPORT) < 1.0)
    }

    @Test fun decompositionSumsExactlyPerTopic() {
        val d = RiverAnalysis.decompose(
            stream0 = m(Topic.CONFLICT to 100, Topic.SPORT to 100),
            read0 = m(Topic.CONFLICT to 10, Topic.SPORT to 30),
            stream1 = m(Topic.CONFLICT to 200, Topic.SPORT to 100),
            read1 = m(Topic.CONFLICT to 40, Topic.SPORT to 20),
        )
        for (t in d) {
            assertEquals("supply+selection==Δ for ${t.topic}", t.observedDelta, t.supply + t.selection, 1e-12)
            assertTrue("residual ~0 for ${t.topic}", abs(t.residual) < 1e-12)
        }
        // Observed deltas across topics sum to ~0 (both are share distributions).
        assertEquals(0.0, d.sumOf { it.observedDelta }, 1e-12)
    }

    @Test fun decompositionHandlesNewAndVanishingTopics() {
        // TECH appears only in period 1; SPORT vanishes from the stream in period 1.
        val d = RiverAnalysis.decompose(
            stream0 = m(Topic.SPORT to 100, Topic.POLITICS to 100),
            read0 = m(Topic.SPORT to 20, Topic.POLITICS to 20),
            stream1 = m(Topic.POLITICS to 100, Topic.TECH to 100),
            read1 = m(Topic.POLITICS to 10, Topic.TECH to 30),
        )
        for (t in d) {
            assertEquals("Δ decomposes exactly for ${t.topic}", t.observedDelta, t.supply + t.selection, 1e-12)
        }
    }

    @Test fun decompositionExactOverDeterministicRandomCorpus() {
        // Seeded LCG (no Math.random — determinism for resume + reproducibility).
        var seed = 0x9E3779B97F4A7C15uL
        fun next(bound: Int): Int {
            seed = seed * 6364136223846793005uL + 1442695040888963407uL
            return ((seed shr 33).toLong() % bound).toInt().let { if (it < 0) it + bound else it }
        }
        val topics = Topic.entries
        repeat(500) {
            // Domain invariant: you can only read what flowed. Generate stream
            // first, then read_i in [0, stream_i]. The decomposition identity is
            // exact precisely under this invariant (which the aggregate pipeline
            // guarantees).
            fun streamAndRead(): Pair<Map<Topic, Int>, Map<Topic, Int>> {
                val stream = topics.associateWith { next(50) }.filterValues { it > 0 }
                val read = stream.mapValues { (_, s) -> next(s + 1) }
                return stream to read
            }
            val (s0, r0) = streamAndRead()
            val (s1, r1) = streamAndRead()
            val d = RiverAnalysis.decompose(s0, r0, s1, r1)
            for (t in d) {
                assertTrue(
                    "residual too large for ${t.topic}: ${t.residual}",
                    abs(t.residual) < 1e-9,
                )
            }
        }
    }

    @Test fun plainLanguageSplitIsCoherentWhenSameSign() {
        // Conflict's stream doubled and the reader also leaned in: both push the
        // same direction, so the percentages are a sensible "X% supply, Y% you".
        val d = RiverAnalysis.decompose(
            stream0 = m(Topic.CONFLICT to 100, Topic.CULTURE to 300),
            read0 = m(Topic.CONFLICT to 5, Topic.CULTURE to 45),
            stream1 = m(Topic.CONFLICT to 300, Topic.CULTURE to 300),
            read1 = m(Topic.CONFLICT to 40, Topic.CULTURE to 30),
        )
        val conflict = d.first { it.topic == Topic.CONFLICT }
        assertTrue("conflict intake rose", conflict.observedDelta > 0)
        val sp = conflict.supplyPercent!!
        val se = conflict.selectionPercent!!
        assertEquals(100.0, sp + se, 1e-9)
    }
}
