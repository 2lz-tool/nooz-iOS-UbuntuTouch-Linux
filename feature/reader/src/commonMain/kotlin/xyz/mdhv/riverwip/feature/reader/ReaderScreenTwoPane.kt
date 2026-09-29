package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.feature.lens.LensViewModel
import xyz.mdhv.riverwip.model.ImageStyle
import xyz.mdhv.riverwip.model.PaperGrain
import xyz.mdhv.riverwip.model.ReadMarkStyle
import xyz.mdhv.riverwip.model.ReadingAsideStyle

/** The Material list-detail breakpoint (≥840dp) — the point past which a phone-width single pane starts wasting real space. */
val TWO_PANE_MIN_WIDTH = 840.dp

/** Test tags naming which Stand layout is on screen, so the adaptive switch can be asserted rather than eyeballed. */
const val TWO_PANE_TAG = "stand-two-pane"
const val ONE_PANE_TAG = "stand-one-pane"

/** The list pane's fixed width once split — the detail pane takes whatever's left. */
private val LIST_PANE_WIDTH = 400.dp

/**
 * The tablet/large-screen reading layout (owner: "the list and reading panel
 * visible together" on larger formats). Same [ReaderViewModel] and the exact
 * same [ArticleListScreen]/[ReaderDetailScreen] the phone uses underneath —
 * there's no separate two-pane state to keep in sync, and rotating a
 * tablet (or resizing a desktop window) across the breakpoint hands the same
 * selected article straight from one layout to the other.
 *
 * The phone's lift-and-part drag doesn't apply here at all — both panes are
 * simply always on screen, so [ReaderDetailScreen] is driven with a fixed
 * offset/progress of zero and no-op drag callbacks (its edge-swipe detector
 * still technically listens, per [readerGestures], but with nothing to swipe
 * *to* it just quietly does nothing).
 */
@Composable
fun ReaderScreenTwoPane(
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
    onOpenLoom: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenClippings: () -> Unit,
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

    // Same parked-home convention as the phone (owner #8): rest the most
    // recent article once there's something to show and nothing chosen yet,
    // so the detail pane is never blank — but "resting" still isn't a real
    // read until the reader actually does something with it.
    LaunchedEffect(items, selected) {
        if (selected == null && items.isNotEmpty()) {
            vm.openItem(items.first(), rest = true)
        }
    }

    Row(Modifier.fillMaxSize().testTag(TWO_PANE_TAG)) {
        Box(Modifier.width(LIST_PANE_WIDTH).fillMaxHeight()) {
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
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val sel = selected
            // Newspaper columns need real width to be worth it (owner: "a
            // combination based on the space") — plain single-column below
            // that, same as the phone, rather than cramming two narrow columns.
            val columns = columnsForWidth(maxWidth)
            if (sel == null) {
                // Nothing to rest on yet (an empty library) — the list pane's own
                // empty state already explains why; this pane just stays quiet.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.reader_select_article),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Tokens.Spacing.xl),
                    )
                }
            } else if (columns != null) {
                NewspaperReaderPane(
                    vm = vm,
                    item = sel,
                    showReadingTime = showReadingTime,
                    lensOn = highlightLoadedLanguage,
                    lensDisabledDefaultTerms = lensDisabledDefaultTerms,
                    lensCustomTerms = lensCustomTerms,
                    saved = savedIds.contains(sel.id),
                    paperGrain = paperGrain,
                    showFeedImages = showFeedImages,
                    hideNsfwImages = hideNsfwImages,
                    imageStyle = imageStyle,
                    columns = columns,
                    onToggleLens = onToggleLens,
                    onToggleClip = { vm.toggleClip(sel) },
                    onOpenLoom = onOpenLoom,
                )
            } else {
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
                    offsetX = 0f,
                    progress = 0f,
                    parkedRoom = null,
                    onToggleLens = onToggleLens,
                    onToggleClip = { vm.toggleClip(sel) },
                    onBack = { vm.closeItem() },
                    // No separate "Settings room" to slide to here — both panes
                    // are always on screen (owner's spec for this breakpoint) —
                    // so the mirrored button opens the same Settings tab the
                    // list pane's own gear reaches.
                    onOpenSettings = onOpenEditSettings,
                    onRoomDragStart = {},
                    onDrag = {},
                    onDragEnd = {},
                    onParkedTap = {},
                    onOpenLoom = onOpenLoom,
                    onBrightnessDelta = {},
                    onThemeFlick = {},
                )
            }
        }
    }
}
