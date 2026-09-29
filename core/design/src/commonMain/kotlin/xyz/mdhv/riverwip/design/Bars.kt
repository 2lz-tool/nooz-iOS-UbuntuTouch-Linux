package xyz.mdhv.riverwip.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import org.jetbrains.compose.resources.stringResource
import xyz.mdhv.riverwip.design.res.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.model.Topic

/**
 * The day-mix bar (owner's Stand/Paper mocks): today's stream compressed into
 * one thin multi-colour line — the loom, folded flat. Segment widths are the
 * real per-topic shares (see `DayLoomLayout.dayMix`); colours are the
 * CVD-verified topic palette. Colour is never the only channel: the bar always
 * carries a spoken description, and tapping it opens the full loom.
 *
 * The bar reads as **one** capsule: only its two outer ends are rounded (a
 * whole-bar clip), and segments butt directly against each other with a hard
 * edge — not a row of individually-pilled, gapped beads (owner's #4).
 */
@Composable
fun DayMixBar(
    segments: List<Pair<Topic, Double>>,
    modifier: Modifier = Modifier,
    description: String = "Today's mix. Opens the day loom.",
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .semantics { contentDescription = description },
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        val silhouette = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f, top = 0f, right = size.width, bottom = size.height,
                    topLeftCornerRadius = radius, topRightCornerRadius = radius,
                    bottomRightCornerRadius = radius, bottomLeftCornerRadius = radius,
                ),
            )
        }
        clipPath(silhouette) {
            var x = 0f
            for ((topic, fraction) in segments) {
                val w = (size.width * fraction).toFloat()
                drawRect(
                    color = topic.toComposeColor(),
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                )
                x += w
            }
        }
    }
}

/**
 * The candy-cane bar (owner's empty-Stand mock): a barber-pole red/white
 * stripe that spins while there's nothing to show — fetching, or no data yet.
 * Red-on-white carries no red/green discrimination burden (colour-vision
 * constraint, brief §2), and the motion itself is the signal, paired with the
 * surrounding empty-state copy.
 */
@Composable
fun CandyCaneBar(modifier: Modifier = Modifier, animate: Boolean = true) {
    // Hoisted: a semantics block is not a composable scope.
    val waiting = stringResource(Res.string.bar_waiting)
    val stripe = Color(0xFFE03131)
    val ground = Color(0xFFFFFFFF)
    val transition = rememberInfiniteTransition(label = "candy")
    val phase = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "candyPhase",
        ).value
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .semantics { contentDescription = waiting },
    ) {
        val h = size.height
        val stripeW = h * 2.2f
        val period = stripeW * 2
        // Same whole-bar rounded-capsule clip as DayMixBar (owner: "the
        // corners are not rounded as they should be") — a plain clipRect
        // left this one alone with hard square corners while every other
        // state of this same bar reads as a single rounded capsule.
        val radius = CornerRadius(h / 2f, h / 2f)
        val silhouette = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f, top = 0f, right = size.width, bottom = h,
                    topLeftCornerRadius = radius, topRightCornerRadius = radius,
                    bottomRightCornerRadius = radius, bottomLeftCornerRadius = radius,
                ),
            )
        }
        clipPath(silhouette) {
            drawRect(ground, size = size)
            var x = -period + phase * period
            while (x < size.width + period) {
                val path = Path().apply {
                    moveTo(x, h)
                    lineTo(x + h, 0f)
                    lineTo(x + h + stripeW, 0f)
                    lineTo(x + stripeW, h)
                    close()
                }
                drawPath(path, stripe)
                x += period
            }
        }
    }
}
