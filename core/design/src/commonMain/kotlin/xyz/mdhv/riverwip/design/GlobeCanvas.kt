package xyz.mdhv.riverwip.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import xyz.mdhv.riverwip.design.res.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.model.GlobeModel
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.Topic

/**
 * The region-picker globe (owner's interactive reference): a dotted
 * orthographic earth — drag to spin, pinch to widen the selection band — inside
 * a ring showing the aimed region's real topic mix. All projection/sector math
 * is the pure [GlobeModel]; colours are the CVD-verified palette. Lives in
 * `:core:design` (not a feature module) so it stays available to any picker
 * that needs it, not just Edit's. The globe is a shortcut, never the only
 * door: the sector label beneath it names the selection, and the topic chips
 * beside it do the same job without gestures.
 */
@Composable
fun GlobeCanvas(
    yaw: Double,
    pitch: Double,
    bandHalf: Double,
    ringMix: Map<Topic, Int>,
    onSpin: (dYaw: Double, dPitch: Double) -> Unit,
    onZoomBand: (factor: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val region = if (bandHalf >= GlobeModel.GLOBAL_BAND_THRESHOLD) {
        Region.GLOBAL
    } else {
        Region.forLongitude(GlobeModel.centerLongitude(yaw))
    }
    val dotSelected = MaterialTheme.colorScheme.onBackground
    val dotDim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val sphere = MaterialTheme.colorScheme.surfaceVariant
    val rim = MaterialTheme.colorScheme.outlineVariant
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    // Resolved before `semantics { }`, which is not a composable scope.
    val spokenDescription = describeGlobe(region, bandHalf, ringMix)
    val spinWest = stringResource(Res.string.globe_spin_west)
    val spinEast = stringResource(Res.string.globe_spin_east)
    val widen = stringResource(Res.string.globe_widen)
    val narrow = stringResource(Res.string.globe_narrow)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics(mergeDescendants = true) {
                contentDescription = spokenDescription
                // The globe used to say "Drag to spin, pinch to widen the
                // band" to a reader who can make neither gesture, and the
                // topic-mix ring — the only place the aimed region's actual
                // numbers are drawn — existed nowhere in speech at all. The
                // region chips beneath cover picking a sector, but nothing
                // covered the band width, so one of the two things this
                // control does had no non-gesture route.
                customActions = listOf(
                    CustomAccessibilityAction(spinWest) { onSpin(-SPIN_STEP, 0.0); true },
                    CustomAccessibilityAction(spinEast) { onSpin(SPIN_STEP, 0.0); true },
                    CustomAccessibilityAction(widen) { onZoomBand(WIDEN_STEP); true },
                    CustomAccessibilityAction(narrow) { onZoomBand(1.0 / WIDEN_STEP); true },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f) onZoomBand(zoom.toDouble())
                    // Horizontal spin only (owner): the earth yaws around its
                    // axis; it never tips. The vertical pan component is dropped
                    // so the poles stay put and regions read consistently.
                    if (pan.x != 0f) onSpin(pan.x * 0.4, 0.0)
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val ringOuter = size.minDimension / 2f - 2.dp.toPx()
            val ringWidth = 11.dp.toPx()
            val r = ringOuter - ringWidth - 15.dp.toPx()

            // Topic-mix ring (real counts; empty mix = quiet hairline ring).
            val total = ringMix.values.sum()
            if (total > 0) {
                var startDeg = -90f
                for (topic in Topic.entries) {
                    val v = ringMix[topic] ?: 0
                    if (v == 0) continue
                    val sweep = 360f * v / total
                    drawArc(
                        color = topic.toComposeColor(),
                        startAngle = startDeg,
                        sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(cx - ringOuter + ringWidth / 2, cy - ringOuter + ringWidth / 2),
                        size = androidx.compose.ui.geometry.Size((ringOuter - ringWidth / 2) * 2, (ringOuter - ringWidth / 2) * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth),
                    )
                    startDeg += sweep
                }
            } else {
                drawCircle(rim, radius = ringOuter - ringWidth / 2, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(ringWidth / 3))
            }

            // Sphere + dotted land.
            drawCircle(sphere, radius = r, center = Offset(cx, cy))
            drawCircle(rim, radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            for (dot in GlobeModel.dots) {
                val p = GlobeModel.project(dot[0], dot[1], yaw, pitch) ?: continue
                val shade = (0.45 + 0.55 * kotlin.math.cos(p.distance)).toFloat()
                val selected = GlobeModel.inBand(dot[0], yaw, bandHalf)
                drawCircle(
                    color = if (selected) dotSelected.copy(alpha = 0.6f + 0.4f * shade) else dotDim,
                    radius = (1.3f + 1.1f * shade).dp.toPx() * 0.9f,
                    center = Offset(cx + (p.x * r).toFloat(), cy + (p.y * r).toFloat()),
                )
            }

            // Band guides: the selected slice's edges.
            if (bandHalf < GlobeModel.GLOBAL_BAND_THRESHOLD) {
                val half = ((bandHalf / 180.0) * r * 0.7).toFloat().coerceAtLeast(1f)
                drawLine(guide, Offset(cx - half, cy - r - 6.dp.toPx()), Offset(cx - half, cy + r + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
                drawLine(guide, Offset(cx + half, cy - r - 6.dp.toPx()), Offset(cx + half, cy + r + 6.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

/**
 * One step of a spin or a zoom, for a reader driving this by custom action
 * rather than by finger. Sized so a few repeats visibly move the selection:
 * an action menu is read aloud one item at a time, so an increment that needs
 * twenty invocations to do anything is the same as no increment.
 */
private const val SPIN_STEP = 22.0
private const val WIDEN_STEP = 1.35

/**
 * What the globe says out loud.
 *
 * The ring is the only place the aimed region's topic mix is drawn, so it has
 * to be spoken here or it does not exist for a screen-reader user. Named in
 * descending order and capped, because this is read in one breath before
 * anything else on the screen.
 *
 * `@Composable` so it can reach `stringResource`: assembled from literals in a
 * plain function it was English in every locale, and invisible to `verifyI18n`,
 * which matches text call sites rather than string construction.
 */
@Composable
private fun describeGlobe(region: Region, bandHalf: Double, ringMix: Map<Topic, Int>): String {
    val total = ringMix.values.sum()
    val band = if (bandHalf >= GlobeModel.GLOBAL_BAND_THRESHOLD) {
        stringResource(Res.string.globe_band_world)
    } else {
        stringResource(Res.string.globe_band_degrees, (bandHalf * 2).toInt())
    }
    val head = stringResource(Res.string.globe_aimed, region.label, band)
    if (total == 0) return head + " " + stringResource(Res.string.globe_nothing_flowed)

    val named = ringMix.entries
        .filter { it.value > 0 }
        .sortedByDescending { it.value }
        .take(SPOKEN_RING_TOPICS)
    val rest = ringMix.entries.count { it.value > 0 } - named.size
    // `map` is inline and so is a composable scope; `joinToString`'s transform
    // is not, which is why the pieces are resolved before being joined.
    var mix = named
        .map { stringResource(Res.string.loom_topic_count, it.key.placeholderLabel, it.value) }
        .joinToString(", ")
    if (rest > 0) mix += stringResource(Res.string.loom_and_more_topics, rest)
    return head + " " + stringResource(Res.string.globe_mix, total, mix)
}

private const val SPOKEN_RING_TOPICS = 4
