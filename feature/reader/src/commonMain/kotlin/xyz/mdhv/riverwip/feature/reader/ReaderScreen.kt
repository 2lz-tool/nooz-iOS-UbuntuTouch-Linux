package xyz.mdhv.riverwip.feature.reader

import xyz.mdhv.riverwip.design.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.feature.lens.LensViewModel
import xyz.mdhv.riverwip.model.ImageStyle
import xyz.mdhv.riverwip.model.PaperGrain
import xyz.mdhv.riverwip.model.ReadMarkStyle
import xyz.mdhv.riverwip.model.ReadingAsideStyle
import kotlin.math.abs

private const val PARK_THRESHOLD = 0.45f
private const val FLICK_VELOCITY_DP_PER_SEC = 1200f
private const val SETTLE_MS = 300

/**
 * The reader surface. Home is the parked lift-and-part state (owner #8): the
 * Stand list with the most-recent article resting as a peeking card on the
 * right. That rest is a real reader — tap or drag it in to read (its scroll
 * position is preserved), and it never counts as read until you actually
 * engage it. Opening any article (tapping the list, a framing) brings it in
 * full; closing returns to the parked home. With no content yet, the full
 * Stand shows instead — its empty state is the first-run guidance.
 *
 * Lift-and-part navigation: an edge-origin one-finger drag drives one
 * `progress` value directly off the finger — Paper shrinks, shadows, rounds
 * and translates while the room it heads toward (Stand left, Settings right)
 * sits still behind, inset by the peek so nothing hides under the card. Only
 * release eases a settle; parking preserves the reader's exact scroll state.
 */
@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    lensVm: LensViewModel,
    showReadingTime: Boolean,
    highlightLoadedLanguage: Boolean,
    immersiveReader: Boolean,
    noozFlashEnabled: Boolean,
    noozCastEnabled: Boolean,
    todayInHistoryEnabled: Boolean,
    paperGrain: PaperGrain,
    readMarkStyle: ReadMarkStyle,
    unreadPinchFilter: Boolean,
    lensDisabledDefaultTerms: Set<String>,
    lensCustomTerms: Set<String>,
    showFeedImages: Boolean,
    hideNsfwImages: Boolean,
    imageStyle: ImageStyle,
    readingAsideStyle: ReadingAsideStyle,
    onToggleLens: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenEditSettings: () -> Unit,
    // The right-hand room's content, supplied by the caller so this feature
    // module never needs a dependency on wherever Settings lives — `onBack` is
    // this screen's own un-park.
    settingsRoom: @Composable (onBack: () -> Unit) -> Unit,
    onOpenLoom: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenClippings: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onThemeFlick: () -> Unit,
) {
    LaunchedEffect(highlightLoadedLanguage) {
        lensVm.updateUnderlinesEnabled(highlightLoadedLanguage)
    }
    LaunchedEffect(lensDisabledDefaultTerms) {
        lensVm.updateLensDisabledDefaultTerms(lensDisabledDefaultTerms)
    }
    LaunchedEffect(lensCustomTerms) {
        lensVm.updateLensCustomTerms(lensCustomTerms)
    }

    val selected = vm.selectedItem
    val items by vm.items.collectAsStateWithLifecycle()
    val savedIds by vm.savedIds.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var containerWidth by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var enteredId by remember { mutableStateOf<String?>(null) }
    // Which room Paper rests over as a floating card — null while full-screen.
    var parkedRoom by remember { mutableStateOf<ReaderRoom?>(null) }
    // The room a fresh edge drag heads toward, fixed for the gesture's duration.
    var draggingRoom by remember { mutableStateOf<ReaderRoom?>(null) }

    val peekDp = 64.dp
    val peekPx = with(LocalDensity.current) { peekDp.toPx() }
    val flickVelocityPx = with(LocalDensity.current) { FLICK_VELOCITY_DP_PER_SEC.dp.toPx() }

    // A plain function, not a `val`: the gesture's pointerInput relaunches only
    // when `parkedRoom` changes, so callbacks it captures must read
    // `containerWidth` (state) live rather than off a stale composition.
    fun dragRange(): Float {
        val scaleInsetPx = containerWidth * (1f - PAPER_MIN_SCALE) / 2f
        return (containerWidth - peekPx - scaleInsetPx).coerceAtLeast(1f)
    }

    // 0f = full Paper, 1f = fully parked — drives scale/shadow/corner/slide.
    val progress = (abs(offsetX) / dragRange()).coerceIn(0f, 1f)

    // Reset the rig whenever nothing is open (a transient on close, before the
    // parked home re-selects).
    LaunchedEffect(selected) {
        if (selected == null) {
            offsetX = 0f
            enteredId = null
            parkedRoom = null
            draggingRoom = null
        }
    }

    fun settle(velocityX: Float) {
        val room = draggingRoom ?: parkedRoom ?: return
        if (containerWidth <= 0) return
        val range = dragRange()
        val liveProgress = (abs(offsetX) / range).coerceIn(0f, 1f)
        val towardPark = if (room == ReaderRoom.STAND) velocityX else -velocityX
        val committing = liveProgress > PARK_THRESHOLD || towardPark > flickVelocityPx
        val target = when {
            !committing -> 0f
            room == ReaderRoom.STAND -> range
            else -> -range
        }
        scope.launch {
            animate(offsetX, target, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { v, _ -> offsetX = v }
            parkedRoom = if (committing) room else null
            draggingRoom = null
            // Settling back to full from a park means the reader engaged the peek.
            if (!committing) vm.markEngaged()
        }
    }

    // Un-park back to full Paper (the card's tap, Settings' own back, hardware
    // back). Bringing a resting home peek in full makes it a real read session.
    fun unpark() {
        if (parkedRoom == null) return
        scope.launch {
            animate(offsetX, 0f, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { v, _ -> offsetX = v }
            parkedRoom = null
            draggingRoom = null
            vm.markEngaged()
        }
    }

    // The visible back control: slide the sheet off to the right, then close —
    // which returns to the parked home (the next re-select).
    fun slideBack() {
        val w = containerWidth.toFloat()
        scope.launch {
            if (w > 0f) animate(offsetX, w, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { v, _ -> offsetX = v }
            vm.closeItem()
        }
    }

    // The back button's mirror: an explicit settings control parks left the
    // same way a real drag-and-commit does, rather than duplicating the drag
    // rig's own math a second time.
    fun openSettings() {
        if (containerWidth <= 0) return
        val range = dragRange()
        scope.launch {
            animate(offsetX, -range, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { v, _ -> offsetX = v }
            parkedRoom = ReaderRoom.SETTINGS
            draggingRoom = null
        }
    }

    // Hardware back: parked → full, full → closed (→ parked home).
    BackHandler(enabled = selected != null) {
        if (parkedRoom != null) unpark() else vm.closeItem()
    }

    Box(Modifier.fillMaxSize().testTag(ONE_PANE_TAG).onSizeChanged { containerWidth = it.width }) {
        // Parked home (owner #8): with content and nothing open, rest the most
        // recent article as a peek over the Stand — never recorded as read.
        LaunchedEffect(items, selected, containerWidth) {
            if (selected == null && items.isNotEmpty() && containerWidth > 0) {
                vm.openItem(items.first(), rest = true)
            }
        }

        val sel = selected
        if (sel == null) {
            // No article yet: the full Stand. Its empty state is the first-run
            // guidance (add sources, pull the latest) — the manual when there's
            // nothing to read.
            ArticleListScreen(
                vm = vm,
                noozFlashEnabled = noozFlashEnabled,
                noozCastEnabled = noozCastEnabled,
                todayInHistoryEnabled = todayInHistoryEnabled,
                readMarkStyle = readMarkStyle,
                unreadPinchFilter = unreadPinchFilter,
                showFeedImages = showFeedImages,
                hideNsfwImages = hideNsfwImages,
                imageStyle = imageStyle,
                onOpenItem = { vm.openItem(it) },
                onOpenEdit = onOpenEdit,
                onOpenEditSettings = onOpenEditSettings,
                onOpenLoom = onOpenLoom,
                onOpenDatePicker = onOpenDatePicker,
                onOpenClippings = onOpenClippings,
            )
            return@Box
        }

        // Entry: a resting home peek parks toward the Stand with no slide; a
        // real open slides in full from the right.
        LaunchedEffect(sel.id, containerWidth) {
            if (containerWidth > 0 && enteredId != sel.id) {
                draggingRoom = null
                if (vm.openedAtRest) {
                    offsetX = dragRange()
                    parkedRoom = ReaderRoom.STAND
                } else {
                    parkedRoom = null
                    offsetX = containerWidth.toFloat()
                    animate(offsetX, 0f, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { v, _ -> offsetX = v }
                }
                enteredId = sel.id
            }
        }

        // The Stand, sitting still behind, inset by the peek so its content
        // never hides under the parked card.
        if (offsetX > 0f) {
            Box(Modifier.fillMaxSize().padding(end = peekDp)) {
                ArticleListScreen(
                    vm = vm,
                    noozFlashEnabled = noozFlashEnabled,
                    noozCastEnabled = noozCastEnabled,
                    todayInHistoryEnabled = todayInHistoryEnabled,
                    readMarkStyle = readMarkStyle,
                    unreadPinchFilter = unreadPinchFilter,
                    showFeedImages = showFeedImages,
                    hideNsfwImages = hideNsfwImages,
                    imageStyle = imageStyle,
                    onOpenItem = { vm.openItem(it) },
                    onOpenEdit = onOpenEdit,
                    onOpenEditSettings = onOpenEditSettings,
                    onOpenLoom = onOpenLoom,
                    onOpenDatePicker = onOpenDatePicker,
                    onOpenClippings = onOpenClippings,
                )
            }
        }
        // Settings, the symmetric second room, inset on the left.
        if (offsetX < 0f) {
            Box(Modifier.fillMaxSize().padding(start = peekDp)) {
                settingsRoom { unpark() }
            }
        }
        ReaderDetailScreen(
            vm = vm,
            lensVm = lensVm,
            item = sel,
            showReadingTime = showReadingTime,
            lensOn = highlightLoadedLanguage,
            saved = savedIds.contains(sel.id),
            immersive = immersiveReader,
            paperGrain = paperGrain,
            showFeedImages = showFeedImages,
            hideNsfwImages = hideNsfwImages,
            imageStyle = imageStyle,
            readingAsideStyle = readingAsideStyle,
            offsetX = offsetX,
            progress = progress,
            parkedRoom = parkedRoom,
            onToggleLens = onToggleLens,
            onToggleClip = { vm.toggleClip(sel) },
            onBack = { slideBack() },
            onOpenSettings = { openSettings() },
            onRoomDragStart = { room -> draggingRoom = room },
            onDrag = { dx ->
                val room = draggingRoom ?: parkedRoom
                if (room != null && containerWidth > 0) {
                    val range = dragRange()
                    offsetX = when (room) {
                        ReaderRoom.STAND -> (offsetX + dx).coerceIn(0f, range)
                        ReaderRoom.SETTINGS -> (offsetX + dx).coerceIn(-range, 0f)
                    }
                }
            },
            onDragEnd = { velocity -> settle(velocity) },
            onParkedTap = { unpark() },
            onOpenLoom = onOpenLoom,
            onBrightnessDelta = onBrightnessDelta,
            onThemeFlick = onThemeFlick,
        )
    }
}
