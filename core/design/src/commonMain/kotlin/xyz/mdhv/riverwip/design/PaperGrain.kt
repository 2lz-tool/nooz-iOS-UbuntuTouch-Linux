package xyz.mdhv.riverwip.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.model.PaperGrain
import kotlin.random.Random

// Each speckle carries its own radius so the field reads as paper fibre, not a
// stamped polka-dot grid — uniform dots were the "big ugly dots" the owner saw.
private class GrainSpec(
    val cellDp: Float,
    val minRadiusDp: Float,
    val maxRadiusDp: Float,
    val alpha: Float,
)

private fun specFor(grain: PaperGrain): GrainSpec? = when (grain) {
    PaperGrain.NONE -> null
    // Fine: a tight, almost-even mist of sub-pixel specks.
    PaperGrain.FINE -> GrainSpec(cellDp = 5f, minRadiusDp = 0.3f, maxRadiusDp = 0.7f, alpha = 0.05f)
    // Coarse: the same fibrous mist, just a larger weave and a touch more
    // present. It stays dense with varied dot sizes so it reads as a coarser
    // *grain*, not the sparse, uniform blobs a naive "bigger dots" step gives.
    PaperGrain.COARSE -> GrainSpec(cellDp = 7f, minRadiusDp = 0.35f, maxRadiusDp = 1.0f, alpha = 0.06f)
}

// A fixed seed, not one rolled per call site: the grain is a property of the
// paper texture itself, not of any one article or clipping — it should look
// identical everywhere it's drawn, not re-rolled screen to screen.
private const val GRAIN_SEED = 5851L

private class Speckle(val center: Offset, val radius: Float)

/**
 * A speckled paper-grain texture (owner's ask, 2026-07): three fixed steps —
 * "simplistic and minimal," texture felt rather than fine-tuned with a
 * slider. Draws behind this node's own content (never over text), and only
 * recomputes its speckle positions when the size changes, not every frame —
 * [ink] should already read legibly against the surface it sits on; the
 * texture only ever darkens it a little. [grain] of [PaperGrain.NONE] is a
 * no-op so callers can apply this unconditionally.
 */
fun Modifier.paperGrain(grain: PaperGrain, ink: Color): Modifier {
    val spec = specFor(grain) ?: return this
    val color = ink.copy(alpha = spec.alpha)
    return this.drawWithCache {
        val cellPx = spec.cellDp.dp.toPx()
        val minRadiusPx = spec.minRadiusDp.dp.toPx()
        val radiusSpanPx = (spec.maxRadiusDp - spec.minRadiusDp).dp.toPx()
        val cols = (size.width / cellPx).toInt() + 2
        val rows = (size.height / cellPx).toInt() + 2
        val rnd = Random(GRAIN_SEED)
        val speckles = ArrayList<Speckle>(cols * rows)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val jitterX = rnd.nextFloat() * cellPx
                val jitterY = rnd.nextFloat() * cellPx
                val radius = minRadiusPx + rnd.nextFloat() * radiusSpanPx
                speckles += Speckle(Offset(col * cellPx + jitterX, row * cellPx + jitterY), radius)
            }
        }
        onDrawBehind {
            for (speckle in speckles) drawCircle(color, speckle.radius, speckle.center)
        }
    }
}
