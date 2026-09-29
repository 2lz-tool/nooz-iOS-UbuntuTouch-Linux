package xyz.mdhv.riverwip.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft top edge for a scrolling region (owner, 2026-07: "the headers behind
 * which the menu scrolls away should be a gradient falloff, it's a hard edge
 * currently"). Content dissolves into the header instead of ending on a hard
 * clip line.
 *
 * It's an alpha mask, not an overlay: the top [height] of this node fades its
 * own contents to transparent (a vertical Transparent→opaque gradient composited
 * with [BlendMode.DstIn]), so it reveals whatever sits behind — header or plain
 * background — without needing to know that surface's colour, and it stays
 * correct across themes. The offscreen compositing strategy keeps the blend
 * local to this node.
 *
 * [active] gates it on there actually being content scrolled above: pass the
 * scroll state's `canScrollBackward` so the top rows are crisp at rest and only
 * fade once something has scrolled up under the header. When false this is a
 * no-op (no offscreen layer, no cost). Place it on the scrolling node itself,
 * above the scroll modifier, so the fade is measured from the top of the
 * viewport.
 */
fun Modifier.topFadingEdge(active: Boolean, height: Dp = 24.dp): Modifier {
    if (!active) return this
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadePx = height.toPx()
            if (fadePx <= 0f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startY = 0f,
                    endY = fadePx,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
}
