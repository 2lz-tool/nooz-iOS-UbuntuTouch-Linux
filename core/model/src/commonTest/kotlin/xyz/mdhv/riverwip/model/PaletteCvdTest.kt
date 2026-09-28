package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour-vision guarantee (brief §8): the topic palette must remain pairwise
 * distinguishable under simulated protanopia and deuteranopia. **This test fails
 * the build if any pair collapses** — the primary user is red–green colourblind.
 */
class PaletteCvdTest {

    // CIE76 ΔE threshold for "distinguishable under CVD". The current palette's
    // worst pair sits at ~15.5, so 12 leaves margin while still guarding regressions.
    private val THRESHOLD = 12.0

    @Test fun everyTopicHasAColor() {
        for (t in Topic.entries) assertTrue("missing color for $t", TopicPalette.colors.containsKey(t))
        assertEquals(Topic.entries.size, TopicPalette.colors.size)
    }

    @Test fun palettePairwiseDistinctUnderProtanopiaAndDeuteranopia() {
        val entries = TopicPalette.colors.entries.toList()
        val failures = ArrayList<String>()
        for (type in listOf(Cvd.Type.PROTANOPIA, Cvd.Type.DEUTERANOPIA)) {
            for (i in entries.indices) for (j in i + 1 until entries.size) {
                val d = Cvd.deltaEUnder(entries[i].value, entries[j].value, type)
                if (d < THRESHOLD) {
                    failures.add("$type: ${entries[i].key.key} vs ${entries[j].key.key} ΔE=%.1f".format(d))
                }
            }
        }
        assertTrue(
            "topic colors collapse under CVD (must be ≥ $THRESHOLD):\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test fun palettePairwiseDistinctUnderNormalVision() {
        val entries = TopicPalette.colors.entries.toList()
        for (i in entries.indices) for (j in i + 1 until entries.size) {
            val d = Cvd.deltaE(entries[i].value, entries[j].value)
            assertTrue(
                "${entries[i].key.key} vs ${entries[j].key.key} too close under normal vision: %.1f".format(d),
                d >= THRESHOLD,
            )
        }
    }

    @Test fun cvdMathSanity() {
        val teal = TopicPalette.colorFor(Topic.SCIENCE)
        assertEquals(0.0, Cvd.deltaE(teal, teal), 1e-9)
        assertEquals("NORMAL is identity", teal, Cvd.simulate(teal, Cvd.Type.NORMAL))
        // A pure red and pure green collapse toward each other under deuteranopia
        // (the very confusion the palette must avoid) — sanity that the sim works.
        val red = Cvd.argb(220, 50, 47); val green = Cvd.argb(50, 160, 80)
        assertTrue(
            Cvd.deltaEUnder(red, green, Cvd.Type.DEUTERANOPIA) < Cvd.deltaE(red, green),
        )
    }
}
