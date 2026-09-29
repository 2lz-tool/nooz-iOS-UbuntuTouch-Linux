package xyz.mdhv.riverwip.design

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.mdhv.riverwip.model.Topic

/**
 * The region globe, asserted rather than asserted-about: the semantics tree must carry
 * the data and the actions a screen reader cannot get from gestures. Runs on the desktop
 * (Skia) compose test host, so it also proves the globe renders and publishes semantics
 * on the Linux target. It does not prove what a screen reader utters.
 */
@OptIn(ExperimentalTestApi::class)
class GlobeAccessibilityTest {

    private val spins = mutableListOf<Double>()
    private val zooms = mutableListOf<Double>()

    @BeforeTest fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun globe(
        ringMix: Map<Topic, Int> = mapOf(Topic.POLITICS to 30, Topic.BUSINESS to 8, Topic.SPORT to 2),
        check: (node: () -> SemanticsNode) -> Unit,
    ) = runComposeUiTest {
        setContent {
            GlobeCanvas(
                yaw = 78.0,
                pitch = 0.0,
                bandHalf = 20.0,
                ringMix = ringMix,
                onSpin = { dYaw, _ -> spins += dYaw },
                onZoomBand = { zooms += it },
            )
        }
        waitForIdle()
        fun walk(node: SemanticsNode): SemanticsNode? {
            if (node.config.contains(SemanticsProperties.ContentDescription)) return node
            for (child in node.children) walk(child)?.let { return it }
            return null
        }
        check { walk(onRoot().fetchSemanticsNode()) ?: error("the globe publishes no contentDescription") }
    }

    private fun SemanticsNode.spoken() =
        config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")

    private fun SemanticsNode.actions() = config.getOrNull(SemanticsActions.CustomActions).orEmpty()

    @Test fun bothGesturesHaveANonGestureRoute() = globe { node ->
        val labels = node().actions().map { it.label }
        assertTrue(labels.any { it.contains("Spin") }, "spinning is reachable: $labels")
        assertTrue(labels.any { it.contains("band") }, "the band is reachable: $labels")
    }

    @Test fun theActionsActuallyMoveTheGlobe() = globe { node ->
        node().actions().first { it.label == "Spin east" }.action()
        node().actions().first { it.label == "Widen the band" }.action()
        assertEquals(1, spins.size, "spun east by one step")
        assertTrue(spins.single() > 0, "spun in the positive direction: $spins")
        assertEquals(1, zooms.size, "zoomed once")
        assertTrue(zooms.single() > 1.0, "widened rather than narrowed: $zooms")
    }

    @Test fun theRingsCountsExistInSpeech() = globe { node ->
        val d = node().spoken()
        assertTrue(d.contains("40"), "gives the total: $d")
        assertTrue(d.contains("Politics 30"), "names the biggest topic with its count: $d")
    }

    @Test fun itNoLongerInstructsGesturesItCannotAccept() = globe { node ->
        val d = node().spoken()
        assertTrue(!d.contains("Drag to spin"), "must not say drag: $d")
        assertTrue(!d.contains("pinch"), "must not say pinch: $d")
    }

    @Test fun anEmptyRegionStillSaysSomethingTrue() = globe(ringMix = emptyMap()) { node ->
        val d = node().spoken()
        assertTrue(d.contains("Nothing has flowed"), "says nothing flowed: $d")
        assertTrue(d.contains("aimed at"), "still names where it is aimed: $d")
    }
}
