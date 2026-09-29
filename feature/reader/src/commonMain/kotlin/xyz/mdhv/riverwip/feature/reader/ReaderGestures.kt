package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Which room a lift-and-part gesture targets — Stand sits to the left, Settings to the right. */
enum class ReaderRoom { STAND, SETTINGS }

/**
 * Lift-and-part physics (owner's brief, 2026-07), shared by [ReaderScreen] —
 * which owns the drag/park state and the parked rest position — and
 * [ReaderDetailScreen], which reads `progress` straight into Paper's visual
 * layer. How far Paper shrinks, and how rounded/shadowed it gets, at a fully
 * parked rest.
 */
const val PAPER_MIN_SCALE = 0.90f
const val PAPER_MAX_CORNER_DP = 20
const val PAPER_MAX_SHADOW_DP = 24

/**
 * The reading room's gestures (owner's spec, 2026-07):
 *  - two-finger vertical drag: a sliding brightness scale (in-app window
 *    brightness — needs no permission);
 *  - two-finger horizontal flick: next theme tint (one flick, one step);
 *  - one-finger horizontal drag, lift-and-part (owner's brief, 2026-07):
 *    while Paper is full-screen, only a drag *starting within the edge zone*
 *    opens a room — an interior swipe is left alone for text/lens
 *    interaction. Once parked, the floating Paper card is a small, clearly
 *    bounded surface, so its own whole-area drag/tap needs no edge gating.
 *
 * Two-finger handling watches the Initial pass and consumes its events so the
 * article's own vertical scroll (and the one-finger slide) don't fight the
 * drag. Every gesture here has a visible, accessible equivalent elsewhere
 * (back = system back / the on-screen arrow; theme and brightness live in
 * Settings) — gestures are shortcuts, never the only door.
 */
fun Modifier.readerGestures(
    parkedRoom: ReaderRoom?,
    onRoomDragStart: (ReaderRoom) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (velocityX: Float) -> Unit,
    onParkedTap: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onThemeFlick: () -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        // Two-finger gestures, seen before children (Initial pass).
        val flickThresholdPx = 90.dp.toPx()
        awaitEachGesture {
            awaitFirstDown(pass = PointerEventPass.Initial)
            var twoFinger = false
            var totalDx = 0f
            var flicked = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    twoFinger = true
                    val dx = pressed.sumOf { it.positionChange().x.toDouble() }.toFloat() / pressed.size
                    val dy = pressed.sumOf { it.positionChange().y.toDouble() }.toFloat() / pressed.size
                    if (abs(dy) > abs(dx)) {
                        // Sliding scale: full screen height ≈ full brightness range.
                        onBrightnessDelta(-dy / size.height)
                    } else {
                        totalDx += dx
                        if (!flicked && abs(totalDx) > flickThresholdPx) {
                            flicked = true
                            onThemeFlick()
                        }
                    }
                    event.changes.forEach { it.consume() }
                } else if (twoFinger) {
                    // Finger lifted mid-gesture: keep consuming until all are up
                    // so neither the list nor the slide inherits a phantom drag.
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
    .pointerInput(parkedRoom) {
        // One-finger horizontal drag. Not parked: gated to the edge zones, one
        // room per edge, so an interior swipe never hijacks the reading
        // gesture. Parked: the whole (small) floating card is fair game, and
        // a drag too short to leave touch-slop resolves as a tap instead.
        // 56dp, not 24dp (owner: "difficult to open") — the wider band gives a
        // comfortable grab and, crucially, extends past the ~20dp the OS claims
        // for its own edge-back gesture, so a drag starting just inside still
        // reaches us instead of being eaten by system navigation.
        val edgeZonePx = if (parkedRoom == null) 56.dp.toPx() else null
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Main)
            val startEdge = if (edgeZonePx != null) {
                when {
                    down.position.x <= edgeZonePx -> ReaderRoom.STAND
                    down.position.x >= size.width - edgeZonePx -> ReaderRoom.SETTINGS
                    else -> null
                }
            } else {
                null
            }
            if (edgeZonePx != null && startEdge == null) {
                // Interior touch while Paper is full-screen: not ours.
                return@awaitEachGesture
            }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var totalDx = 0f
            var totalDy = 0f
            var started = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    if (started) {
                        onDragEnd(tracker.calculateVelocity().x)
                    } else if (parkedRoom != null && abs(totalDx) < touchSlop && abs(totalDy) < touchSlop) {
                        onParkedTap()
                    }
                    break
                }
                val delta = change.positionChange()
                totalDx += delta.x
                totalDy += delta.y
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!started && abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy)) {
                    started = true
                    if (edgeZonePx != null && startEdge != null) onRoomDragStart(startEdge)
                }
                if (started) {
                    onDrag(delta.x)
                    change.consume()
                }
            }
        }
    }
