package xyz.mdhv.riverwip.feature.river

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onRoot
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.mdhv.riverwip.model.Region

/**
 * The read-by-region map, asserted rather than asserted-about.
 *
 * `RegionHeatStrip` was a bare `Canvas`. A Canvas publishes nothing to the
 * accessibility tree, so the entire answer to "where in the world have I been
 * reading?" — the question the Contrast ledger exists to put in front of you —
 * was carried by nothing but the relative darkness of eight rectangles. Silent
 * to a screen reader, and unreadable to anyone who cannot separate two close
 * greys, which is the same information loss by a different route.
 *
 * As with [DayLoomAccessibilityTest], this proves the tree carries the numbers.
 * It does not prove what TalkBack utters, and no device here can.
 */
@OptIn(ExperimentalTestApi::class)
class ContrastAccessibilityTest {

    @BeforeTest fun english() = Locale.setDefault(Locale.ENGLISH)

    /** Descriptions declared anywhere in the tree, in tree order. */
    private fun ComposeUiTest.descriptions(): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.let { out += it.joinToString(" ") }
            node.children.forEach(::walk)
        }
        walk(onRoot().fetchSemanticsNode())
        return out
    }

    private fun ComposeUiTest.heatStripDescription(): String =
        descriptions().firstOrNull { it.startsWith("Reading by region") }
            ?: error("the region map publishes nothing; descriptions were: ${descriptions()}")

    private fun ComposeUiTest.setStrip(reads: Map<Region, Int>, selected: Region = Region.GLOBAL) {
        setContent {
            RegionHeatStrip(reads = reads, selected = selected, ink = androidx.compose.ui.graphics.Color.Black)
        }
    }

    @Test fun everyRegionWithAReadIsNamedWithItsCount() = runComposeUiTest {
        setStrip(
            mapOf(
                Region.SOUTH_ASIA to 14,
                Region.EUROPE_AFRICA to 3,
                Region.AMERICAS to 9,
            ),
        )
        val spoken = heatStripDescription()

        assertTrue(spoken.contains("14"), "names the densest region and its count: $spoken")
        assertTrue(spoken.contains("3"), "names a quieter one too: $spoken")
        assertTrue(spoken.contains("26"), "gives the total: $spoken")
    }

    @Test fun theSelectionIsSaidOutLoudRatherThanOnlyHighlighted() = runComposeUiTest {
        setStrip(mapOf(Region.EUROPE_AFRICA to 2), selected = Region.EUROPE_AFRICA)
        val spoken = heatStripDescription()

        assertTrue(
            spoken.contains(Region.EUROPE_AFRICA.label),
            "the current sector is spoken, not left to a highlight: $spoken",
        )
    }

    @Test fun anEmptyMapStillSaysSomethingTrue() = runComposeUiTest {
        setStrip(emptyMap())
        val spoken = heatStripDescription()

        assertTrue(spoken.contains("Nothing read yet"), "says there is nothing yet: $spoken")
        // And does not claim a total it does not have.
        assertTrue(!spoken.contains(" 0 "), "no invented count: $spoken")
    }

    @Test fun regionsWithNoReadsAreNotEnumerated() = runComposeUiTest {
        setStrip(mapOf(Region.SOUTH_ASIA to 5))
        val spoken = heatStripDescription()

        // Reading eight zeroes aloud before the one number that matters buries
        // the answer; the description names only what actually happened.
        assertTrue(spoken.contains("South Asia"), "only the region with reads is named: $spoken")
        assertTrue(
            !spoken.contains("${Region.EUROPE_AFRICA.label} 0"),
            "a region with nothing read is left out: $spoken",
        )
    }
}
