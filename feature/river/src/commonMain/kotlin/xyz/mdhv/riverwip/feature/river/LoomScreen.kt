package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.model.DayLoomLayout
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.Starters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy")
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The two ways to read a day: the woven loom, the stark contrast ledger.
 * Framings (a cross-source story-clustering comparison) is disabled for now —
 * the clustering matched unrelated articles (owner, 2026-07); the code stays
 * in place (`FramingsPanel.kt`, `StoryClustering.kt`) for whenever it's fixed.
 */
private enum class LoomMode { LOOM, CONTRAST }

/**
 * The loom surface (owner's Viz flow): a single fixed screen — no scroll. The
 * wordmark and the day's facts sit across the top ("2 Sources · Global · 12
 * July 2026" — the date opens the picker), and the day loom fills the rest,
 * every stream fading into the page top and bottom. Reached by pulling the
 * Stand's day bar down; dismissed by **swiping back up** — the reverse of the
 * open — where it lifts, shrinks, and fades as it recedes toward the bar (a
 * continuous morph), past a threshold. The grabber is the accessible equivalent.
 */
@Composable
fun LoomScreen(vm: LoomViewModel, onClose: () -> Unit, onOpenItem: (Item) -> Unit) {
    val days by vm.days.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledSourceCount.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val recentItems by vm.recentItems.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
    val readEvents by vm.readEvents.collectAsStateWithLifecycle()
    val gdeltEnabledCount by vm.gdeltEnabledCount.collectAsStateWithLifecycle()
    // Three ways to read the same day (owner's contrast idea): the woven Loom,
    // the stark Contrast ledger, and the Framings comparison.
    var mode by remember { mutableStateOf(LoomMode.LOOM) }

    if (vm.showDatePicker) {
        LoomDatePicker(
            days = days,
            selectedDayStart = vm.selectedDayStart ?: days.lastOrNull()?.weekStart,
            selectedRangeEnd = vm.selectedRangeEnd,
            onPick = { vm.selectDay(it) },
            onPickRange = { start, end -> vm.selectRange(start, end) },
        )
        return
    }

    val aggregate = vm.aggregateFor(days)
    val loom = remember(aggregate) {
        if (aggregate == null) {
            DayLoomLayout.Loom(emptyList(), 0, 0)
        } else {
            DayLoomLayout.layout(aggregate.streamCountsByTopic, aggregate.readCountsByTopic)
        }
    }

    // Swipe up to recede (the reverse of the Stand's pull-down open): the loom
    // lifts, shrinks, and fades toward the bar it came from — a continuous morph.
    var lift by remember { mutableFloatStateOf(0f) }
    val closeThresholdPx = with(LocalDensity.current) { 150.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = (lift / closeThresholdPx).coerceIn(0f, 1f)
                translationY = -lift
                alpha = 1f - p * 0.55f
                val s = 1f - p * 0.10f
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (lift > closeThresholdPx) onClose()
                        lift = 0f
                    },
                    // dy is negative when dragging up; accumulate the upward lift.
                    onVerticalDrag = { _, dy -> lift = (lift - dy).coerceAtLeast(0f) },
                )
            }
            .padding(top = Tokens.Spacing.sm),
    ) {
        // No grabber handle (owner: "immersive, immersive, immersive"). Swipe up
        // to dismiss — the whole surface takes the gesture — and the system back
        // control returns to the Stand as the accessible equivalent.

        // One header row, matching the Stand's: the wordmark and source count
        // baseline-aligned on the left (owner B6 — the small text used to float
        // off the wordmark's centre), the date always top-right (owner #9 — the
        // same place it sits on the Stand), with day-step chevrons. The region
        // no longer lives here — it moved into the Contrast view (owner #3).
        val rangeEnd = vm.selectedRangeEnd
        val dateLabel = aggregate?.let { agg ->
            val startLabel = DAY_FORMAT.format(Instant.ofEpochMilli(agg.weekStart).atZone(ZoneId.systemDefault()))
            if (rangeEnd == null) {
                startLabel
            } else {
                val endLabel = DAY_FORMAT.format(Instant.ofEpochMilli(rangeEnd).atZone(ZoneId.systemDefault()))
                "$startLabel – $endLabel"
            }
        } ?: "–"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                NoozWordmark(fontSize = 30.sp, modifier = Modifier.alignByBaseline())
                Text(
                    if (enabledCount == 1) {
                        stringResource(Res.string.loom_source_one, enabledCount)
                    } else {
                        stringResource(Res.string.loom_source_many, enabledCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline().padding(start = Tokens.Spacing.sm),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.stepDay(-1, days) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(Res.string.loom_previous_day))
                }
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(onClickLabel = stringResource(Res.string.loom_open_date_picker)) { vm.setDatePickerVisible(true) }
                        .semantics { role = Role.Button }
                        .padding(horizontal = Tokens.Spacing.xxs, vertical = Tokens.Spacing.xxs),
                )
                IconButton(onClick = { vm.stepDay(1, days) }, enabled = vm.canStepForward(days)) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(Res.string.loom_next_day))
                }
            }
        }

        // The mode toggle: the same day, woven, laid bare, or set side by side.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Tokens.Spacing.xxs),
            horizontalArrangement = Arrangement.Center,
        ) {
            ModeTab("Loom", mode == LoomMode.LOOM) { mode = LoomMode.LOOM }
            ModeTab("Contrast", mode == LoomMode.CONTRAST) { mode = LoomMode.CONTRAST }
        }

        // The selected day/range window, shared by the contrast heatmap and framings.
        val windowStart = aggregate?.weekStart ?: 0L
        val windowEndExclusive = (vm.selectedRangeEnd ?: windowStart) + MILLIS_PER_DAY

        when (mode) {
            LoomMode.CONTRAST -> {
                // Reads by region for the heatmap: each read event → its item →
                // its source's region, counted once per article in the window.
                val readsByRegion = remember(readEvents, recentItems, windowStart, windowEndExclusive) {
                    val byId = recentItems.associateBy { it.id }
                    val counted = HashSet<String>()
                    val counts = HashMap<Region, Int>()
                    for (event in readEvents) {
                        if (event.openedAt !in windowStart until windowEndExclusive) continue
                        val item = byId[event.itemId] ?: continue
                        if (!counted.add(event.itemId)) continue
                        val region = Region.forSourceTag(Starters.regionBySourceId[item.sourceId])
                        counts.merge(region, 1, Int::plus)
                    }
                    counts
                }
                ContrastPanel(
                    streamByTopic = aggregate?.streamCountsByTopic ?: emptyMap(),
                    readByTopic = aggregate?.readCountsByTopic ?: emptyMap(),
                    sourceCounts = aggregate?.sourceCounts ?: emptyMap(),
                    filter = filter,
                    enabledSourceCount = enabledCount,
                    readsByRegion = readsByRegion,
                    onSetRegion = { vm.setRegion(it) },
                    onToggleTopic = { vm.toggleTopic(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = Tokens.Spacing.sm),
                )
            }
            LoomMode.LOOM -> if (aggregate == null || loom.totalFlowed == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(Res.string.loom_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Tokens.Spacing.xl),
                        )
                        // Honest, not a bug (the long-pending "fetch content for any
                        // date" item): almost every source here is a live RSS/Atom feed
                        // with no historical query in the protocol at all, so this can
                        // only ever show what a feed is serving right now — never an
                        // archive of some past day.
                        Text(
                            stringResource(Res.string.loom_no_archive),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Tokens.Spacing.xl),
                        )
                        // GDELT DOC 2.0 is the one catalogue provider with a genuine
                        // absolute-date query, so — only when an enabled GDELT source
                        // actually exists — offer a real historical fetch instead of
                        // leaving the affordance to imply something that isn't there.
                        if (gdeltEnabledCount > 0) {
                            HistoricalGdeltAffordance(
                                state = vm.historicalFetchState,
                                onFetch = { vm.fetchHistoricalGdelt(days) },
                                modifier = Modifier.padding(top = Tokens.Spacing.xs),
                            )
                        }
                    }
                }
            } else {
                DayLoomCanvas(
                    loom = loom,
                    enabledSourceCount = enabledCount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = Tokens.Spacing.sm),
                )
            }
        }
    }
}

/**
 * The one real affordance for the long-pending "fetch content for any date"
 * item: GDELT DOC 2.0 has a genuine absolute-date query (see
 * [FeedUrls.gdeltDocForRange]), so an enabled GDELT source can actually be
 * asked for the day currently shown — unlike RSS/Atom, Google News,
 * Mastodon, or the generic `api` kind, which have no such capability. Only
 * ever rendered when the caller has confirmed a GDELT source is enabled.
 */
@Composable
private fun HistoricalGdeltAffordance(
    state: HistoricalFetchState,
    onFetch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is HistoricalFetchState.Loading -> Text(
            stringResource(Res.string.loom_asking_gdelt),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(horizontal = Tokens.Spacing.sm, vertical = Tokens.Spacing.xxs),
        )
        is HistoricalFetchState.Done -> {
            val label = when {
                state.errors.isNotEmpty() -> "GDELT: ${state.errors.first()}"
                state.newItemCount > 0 ->
                    "Found ${state.newItemCount} new ${if (state.newItemCount == 1) "item" else "items"} from GDELT, tap to ask again"
                else -> "GDELT had nothing new for this day, tap to ask again"
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = modifier
                    .clickable(onClickLabel = stringResource(Res.string.loom_ask_gdelt_again)) { onFetch() }
                    .semantics { role = Role.Button }
                    .padding(horizontal = Tokens.Spacing.sm, vertical = Tokens.Spacing.xxs),
            )
        }
        else -> Text(
            stringResource(Res.string.loom_ask_gdelt),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier
                .clickable(onClickLabel = stringResource(Res.string.loom_fetch_gdelt)) { onFetch() }
                .semantics { role = Role.Button }
                .padding(horizontal = Tokens.Spacing.sm, vertical = Tokens.Spacing.xxs),
        )
    }
}

/** One of the two loom modes — a plain, inked-when-active text tab. */
@Composable
private fun ModeTab(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
        color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .clickable(onClickLabel = stringResource(Res.string.loom_show_view, label)) { onClick() }
            .semantics { role = Role.Tab }
            .minimumInteractiveComponentSize()
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xxs),
    )
}
