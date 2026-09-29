package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.AppSearchBar
import xyz.mdhv.riverwip.design.CandyCaneBar
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.DayMixBar
import xyz.mdhv.riverwip.design.EmptyState
import xyz.mdhv.riverwip.design.NoResultsState
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.topFadingEdge
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.model.ArticleSearch
import xyz.mdhv.riverwip.model.Diversifier
import xyz.mdhv.riverwip.model.ImageStyle
import xyz.mdhv.riverwip.model.ReadMarkStyle
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.Topic
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val STAND_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm")

/** Honest empty-state copy for the exact situation — never a silent blank (brief §3). */
private fun emptyBody(enabledCount: Int, isRefreshing: Boolean, last: RefreshResult?, filtered: Boolean): String = when {
    enabledCount == 0 -> "Click here to add sources\nand read the Nooz"
    isRefreshing -> "Fetching stories ${Copy.fromSources(enabledCount)}…"
    filtered -> "Nothing under this region and topic mix right now. EDIT widens the filter."
    last?.allFailed == true ->
        "Couldn't reach your sources${last.error?.let { " ($it)" } ?: ""}. Tap to try again."
    last != null && last.newItems == 0 -> "Your sources returned nothing new. Tap to check again."
    else -> "Tap to pull the latest stories ${Copy.fromSources(enabledCount)}."
}

/**
 * The Nooz Stand (owner's mocks + flow map, 2026-07): wordmark and date over
 * the day bar — the loom folded flat; candy-cane while empty or fetching —
 * then the region|topics line, then the stand itself. Pulling down past the
 * top stretches the bar and lets go into the full day loom; tapping the bar
 * does the same without the theatre. The region|topics text itself opens
 * Edit's Sources tab; the settings cog beside it opens Edit's Settings tab —
 * Edit shows sources, region/topics, *and* settings now, not a separate gear.
 */
@Composable
fun ArticleListScreen(
    vm: ReaderViewModel,
    noozFlashEnabled: Boolean,
    noozCastEnabled: Boolean,
    todayInHistoryEnabled: Boolean,
    readMarkStyle: ReadMarkStyle,
    unreadPinchFilter: Boolean,
    showFeedImages: Boolean,
    hideNsfwImages: Boolean,
    imageStyle: ImageStyle,
    onOpenItem: (Item) -> Unit,
    onOpenEdit: () -> Unit,
    onOpenEditSettings: () -> Unit,
    onOpenLoom: () -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenClippings: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val readIds by vm.readIds.collectAsStateWithLifecycle()
    val enabledCount by vm.enabledSourceCount.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
    val isRefreshing by vm.isRefreshing.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastRefresh.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val topicCountsForRegion by vm.topicCountsForRegion.collectAsStateWithLifecycle()
    // The Stand's top bar shows what was actually read today, not what merely
    // flowed (owner's #5) — the reader's own bottom utility bar keeps the
    // supply-based mix as ambient context while reading.
    val todayReadMix by vm.todayReadMix.collectAsStateWithLifecycle()

    // Mix for coverage (owner: "one other control... to mix the articles for
    // maximum spread/coverage"). A deterministic round-robin-by-source
    // reorder, not a random shuffle — toggled, not a one-shot re-roll.
    var diversified by rememberSaveable { mutableStateOf(false) }
    // Sort (owner: "filter and sort along with the remix option"). Chronological
    // is the existing default order; grouped-by-source clusters each source's
    // items together (stably, so each group stays newest-first internally).
    // Remix already defines its own explicit order for coverage, so it wins
    // outright when both are on rather than fighting over the final order.
    var groupedBySource by rememberSaveable { mutableStateOf(false) }
    // The region/topic filter already narrows this list end-to-end (Contrast's
    // chips write the same standing filter); this sheet is just a quicker way
    // to reach it from the Stand itself, cascading region -> topic-with-counts.
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    // Search (owner: "search in the listings as well, same as we have in the
    // sources") — same shared bar, same plain-substring-on-title match.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Immersive unread filter (owner's ask): pinch in on the list to show only
    // unread, pinch out to show everything again.
    var showUnreadOnly by rememberSaveable { mutableStateOf(false) }
    val unreadFiltered = if (showUnreadOnly) items.filter { it.id !in readIds } else items
    val q = searchQuery.trim()

    // Body search (D37). Titles and summaries are matched here, in memory,
    // because they exist for every item; bodies are matched by the FTS index in
    // the view model, because they only exist for articles actually opened and
    // matching them is a database query. Searching only one of the two would
    // quietly answer a narrower question than the reader asked.
    val bodyMatches by vm.bodySearchMatches.collectAsStateWithLifecycle()
    LaunchedEffect(q) { vm.searchBodies(q) }
    val terms = remember(q) { ArticleSearch.terms(q) }
    val searched = if (terms.isEmpty()) {
        unreadFiltered
    } else {
        unreadFiltered.filter { item ->
            ArticleSearch.matchesPrefixes(item.title, terms) ||
                item.summary?.let { ArticleSearch.matchesPrefixes(it, terms) } == true ||
                item.id in bodyMatches
        }
    }
    val sorted = if (groupedBySource) searched.sortedBy { sourceTitles[it.sourceId]?.lowercase() ?: "" } else searched
    val displayedItems = if (diversified) remember(sorted) { Diversifier.spread(sorted) } else sorted

    val listState = rememberLazyListState()
    var pull by remember { mutableFloatStateOf(0f) }
    val openThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 140.dp.toPx() }

    // Pull-down-to-loom: overscroll at the top stretches the bar; releasing
    // past the threshold opens the loom.
    val pullConnection = remember(openThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                    pull = (pull + available.y).coerceAtMost(openThresholdPx * 1.4f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pull > openThresholdPx) onOpenLoom()
                pull = 0f
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header: wordmark + today's date.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoozWordmark(fontSize = 30.sp)
            Spacer(Modifier.weight(1f))
            Text(
                STAND_DATE.format(LocalDate.now(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClickLabel = stringResource(Res.string.list_open_date_picker)) { onOpenDatePicker() }
                    .semantics { role = Role.Button }
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = Tokens.Spacing.xs),
            )
        }

        // The bar: today's mix, or the candy cane while there's nothing to show.
        // The pull stretches it toward the loom it stands for.
        val barHeight = 6.dp + with(androidx.compose.ui.platform.LocalDensity.current) {
            (pull / 8f).toDp().coerceAtMost(18.dp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize() // >=48dp tap target; the thin bar centres inside
                .padding(horizontal = Tokens.Spacing.md)
                .height(barHeight)
                .clickable(onClickLabel = stringResource(Res.string.list_open_loom)) { onOpenLoom() },
        ) {
            if (todayReadMix.isEmpty() || isRefreshing) {
                CandyCaneBar(Modifier.fillMaxSize())
            } else {
                DayMixBar(todayReadMix, Modifier.fillMaxSize())
            }
        }

        // Region | Topics line (owner: tapping it opens the current sources —
        // Edit's Sources tab), a quiet refresh, and the settings cog, which now
        // sits where the old "EDIT" text button used to (owner: "let the settings
        // cog sit where the edit button sits now" — Edit itself shows the other
        // settings directly, so this cog no longer needs to hide behind a
        // second tap inside Edit's own header).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                filter.summary(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClickLabel = stringResource(Res.string.list_open_sources)) { onOpenEdit() }
                    .semantics { role = Role.Button }
                    .padding(vertical = Tokens.Spacing.xxs),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showFilterSheet = true }) {
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = stringResource(Res.string.list_filter_region_topic),
                    tint = if (!filter.allTopics || filter.region != Region.GLOBAL) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = { groupedBySource = !groupedBySource }) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = if (groupedBySource) "Grouped by source, tap for newest first" else "Sort: newest first, tap to group by source",
                    tint = if (groupedBySource) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { diversified = !diversified }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = if (diversified) "Showing the mix for coverage, tap to return to flow order" else "Mix for maximum spread",
                    tint = if (diversified) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The unread-only filter used to be reachable *only* by a two-finger
            // pinch on the list — no button, no menu, no accessibility action.
            // A function with no non-gesture route is a function a screen-reader
            // or switch-access user does not have (WCAG 2.5.1, Level A). The
            // pinch still works; it is no longer the only way in.
            IconButton(onClick = { showUnreadOnly = !showUnreadOnly }) {
                Icon(
                    if (showUnreadOnly) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (showUnreadOnly) {
                        stringResource(Res.string.list_showing_unread_only)
                    } else {
                        stringResource(Res.string.list_showing_all)
                    },
                    tint = if (showUnreadOnly) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onOpenClippings) {
                Icon(Icons.Filled.Bookmarks, contentDescription = stringResource(Res.string.list_open_clippings))
            }
            if (isRefreshing) {
                // Resolved before the semantics block, which is not a
                // composable scope.
                val fetching = stringResource(Res.string.list_fetching)
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = Tokens.Spacing.sm)
                        .size(18.dp)
                        .semantics { contentDescription = fetching },
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = { vm.refresh() }, enabled = enabledCount > 0) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(Res.string.list_fetch_now))
                }
            }
            IconButton(onClick = onOpenEditSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(Res.string.list_settings))
            }
        }

        NoozBroadcastCard(
            vm = vm,
            noozFlashEnabled = noozFlashEnabled,
            noozCastEnabled = noozCastEnabled,
            onOpenSetup = onOpenEditSettings,
            modifier = Modifier.padding(horizontal = Tokens.Spacing.md),
        )

        // Immersive unread filter (owner's ask): pinch in shows only unread,
        // pinch out shows everything again. Observes raw pointer positions on
        // the Initial pass without consuming them, so ordinary single-finger
        // scrolling on the list underneath is never interrupted — only a
        // genuine second pointer moves the accumulator at all.
        val pinchModifier = if (unreadPinchFilter) {
            Modifier.pointerInput(unreadPinchFilter) {
                awaitEachGesture {
                    var referenceDistance = 0f
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val pointers = event.changes.filter { it.pressed }
                        if (pointers.size >= 2) {
                            val distance = (pointers[0].position - pointers[1].position).getDistance()
                            if (referenceDistance <= 0f) {
                                referenceDistance = distance
                            } else {
                                val ratio = distance / referenceDistance
                                if (ratio < 0.7f) {
                                    showUnreadOnly = true
                                    referenceDistance = distance
                                } else if (ratio > 1.4f) {
                                    showUnreadOnly = false
                                    referenceDistance = distance
                                }
                            }
                        } else {
                            referenceDistance = 0f
                        }
                        if (event.changes.all { !it.pressed }) break
                    }
                }
            }
        } else {
            Modifier
        }

        when {
            items.isEmpty() -> {
                EmptyStand(
                    enabledCount = enabledCount,
                    isRefreshing = isRefreshing,
                    lastRefresh = lastRefresh,
                    anyUnfiltered = enabledCount > 0 && lastRefresh != null && !filter.allTopics,
                    onAdd = onOpenEdit,
                    onRetry = { vm.refresh() },
                )
            }
            unreadFiltered.isEmpty() -> {
                // Resolved here rather than inline: `clickable`'s onClickLabel
                // is a plain argument, not a composable scope.
                val showAllLabel = stringResource(Res.string.list_show_all_click)
                // showUnreadOnly filtered everything away — an honest, different
                // state from "no sources"/"nothing flowed": there's plenty here,
                // all of it read already.
                Box(Modifier.weight(1f).fillMaxWidth().then(pinchModifier)) {
                    EmptyState(
                        title = stringResource(Res.string.list_nothing_unread),
                        body = stringResource(Res.string.list_nothing_unread_body),
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClickLabel = showAllLabel) { showUnreadOnly = false },
                    )
                }
            }
            displayedItems.isEmpty() -> {
                // Unread items exist, but this search came up empty — distinct
                // from the pinch-filter's stringResource(Res.string.list_nothing_unread) above.
                Box(Modifier.weight(1f).fillMaxWidth().then(pinchModifier)) {
                    NoResultsState()
                }
            }
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth().then(pinchModifier)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().topFadingEdge(listState.canScrollBackward).nestedScroll(pullConnection),
                        // A touch more bottom padding than the list's usual vertical
                        // padding, so the last row clears the scroll-position bar
                        // floating over the bottom edge.
                        contentPadding = PaddingValues(top = Tokens.Spacing.xs, bottom = Tokens.Spacing.lg),
                    ) {
                        // Today in History (owner's ask, 2026-08) stands above
                        // the day's stories the way a printed paper's column
                        // does, and scrolls away with them rather than pinning
                        // itself over the news. Renders nothing when the
                        // setting is off, so the list is untouched for anyone
                        // who left it that way.
                        if (todayInHistoryEnabled) {
                            item(key = "today-in-history") { TodayInHistoryCard(vm = vm) }
                        }
                        items(displayedItems, key = { it.id }) { item ->
                            ItemRow(
                                item = item,
                                sourceTitle = sourceTitles[item.sourceId],
                                // Shown only when the match came from the body:
                                // a result whose headline contains none of the
                                // search terms otherwise just looks like a bug.
                                matchSnippet = bodyMatches[item.id],
                                read = item.id in readIds,
                                readMarkStyle = readMarkStyle,
                                showFeedImages = showFeedImages,
                                hideNsfwImages = hideNsfwImages,
                                imageStyle = imageStyle,
                                onClick = { onOpenItem(item) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        item { EndOfFeed() }
                    }
                    // Where you are in the list (owner: "you missed the bottom scroll
                    // bar which tells you where you are in the reading list") — a
                    // thin track+thumb pinned to the bottom, only once there's more
                    // than one screen's worth to be "somewhere in".
                    ScrollPositionBar(listState = listState, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }

        // The search bar, docked to the bottom (owner: "search in the listings
        // as well, same as we have in the sources") — the same shared bar,
        // hidden only when there's nothing yet to search through at all.
        if (items.isNotEmpty()) {
            AppSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }, placeholder = stringResource(Res.string.list_search))
        }
    }

    if (showFilterSheet) {
        RegionTopicFilterSheet(
            filter = filter,
            topicCounts = topicCountsForRegion,
            onSetRegion = vm::setRegion,
            onToggleTopic = vm::toggleTopic,
            onClearFilter = vm::clearFilter,
            onDismiss = { showFilterSheet = false },
        )
    }
}

@Composable
private fun EndOfFeed() {
    // The river has banks (brief §3): an explicit end, never infinite backfill.
    Box(Modifier.fillMaxWidth().padding(Tokens.Spacing.lg), contentAlignment = Alignment.Center) {
        Text(
            Copy.END_OF_FEED,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A thin scroll-position track+thumb pinned to the bottom of the list (owner:
 * "the bottom scroll bar which tells you where you are in the reading
 * list"). Hidden once everything already fits on one screen — there's
 * nowhere to be "somewhere in" yet.
 */
@Composable
private fun ScrollPositionBar(listState: LazyListState, modifier: Modifier = Modifier) {
    val info = listState.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    if (visible == 0 || total <= visible) return
    val firstVisible = listState.firstVisibleItemIndex
    val startFrac = (firstVisible.toFloat() / total).coerceIn(0f, 1f)
    val sizeFrac = (visible.toFloat() / total).coerceIn(0.06f, 1f)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xs)
            .height(3.dp),
    ) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(h / 2, h / 2)
        drawRoundRect(ink.copy(alpha = 0.14f), size = size, cornerRadius = radius)
        val thumbWidth = (sizeFrac * w).coerceAtLeast(h * 3)
        val thumbLeft = (startFrac * w).coerceAtMost(w - thumbWidth)
        drawRoundRect(
            ink.copy(alpha = 0.55f),
            topLeft = Offset(thumbLeft, 0f),
            size = Size(thumbWidth, h),
            cornerRadius = radius,
        )
    }
}

/**
 * The empty stand (owner's mock): the centred plus button and its invitation.
 * With sources but no stories, the same shape retries the fetch instead.
 */
@Composable
private fun EmptyStand(
    enabledCount: Int,
    isRefreshing: Boolean,
    lastRefresh: RefreshResult?,
    anyUnfiltered: Boolean,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
) {
    val addMode = enabledCount == 0
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = Tokens.Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (addMode) {
            // The owner's own illustration for a reader who hasn't started yet
            // (many strands drawn into one thread, through the needle's eye —
            // the app's own "weave many sources into one stream" idea). The
            // illustration itself is the tap target, not a separate button.
            //
            // Right-aligned and deliberately not full-width (owner's ask): the
            // right edge sits flush with the screen's own edge, while the
            // needle itself — which the drawable draws left-of-centre within
            // its own bounds, not in the middle — lands on the screen's
            // vertical centre line. Both the width and the aspect-derived
            // height are computed explicitly here from BoxWithConstraints's
            // own resolved maxWidth, rather than leaning on fillMaxWidth(
            // fraction) plus Image's own implicit intrinsic-aspect sizing —
            // a first version built that way rendered noticeably smaller and
            // off-centre instead (owner: "illustration still not aligned"),
            // so nothing about its size or position is left for Image or the
            // Column to infer.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val illustrationWidth = maxWidth * NEEDLE_ILLUSTRATION_WIDTH_FRACTION
                Image(
                    painter = painterResource(Res.drawable.img_no_sources),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(illustrationWidth)
                        .aspectRatio(NEEDLE_ILLUSTRATION_ASPECT_RATIO)
                        .clickable(enabled = !isRefreshing, onClickLabel = stringResource(Res.string.list_add_sources)) { onAdd() }
                        .semantics { role = Role.Button },
                )
            }
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = Tokens.Spacing.lg).size(28.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                    .clickable(enabled = !isRefreshing, onClickLabel = stringResource(Res.string.list_fetch_now)) { onRetry() }
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.background,
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.background,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        Text(
            emptyBody(enabledCount, isRefreshing, lastRefresh, anyUnfiltered),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Spacing.lg, start = Tokens.Spacing.xl, end = Tokens.Spacing.xl),
        )
    }
}

/**
 * `img_no_sources`'s own needle column sits at ~38.6% of the drawable's width
 * (measured from the asset, not centred in it — the eye and shaft, not the
 * thread reaching out to either side). Solving `width * (1 - 0.386) = 0.5` for
 * `width` (screen units, right-aligned so the image's right edge is the
 * screen's own right edge) gives the fraction below.
 */
private const val NEEDLE_ILLUSTRATION_WIDTH_FRACTION = 0.81f

/** `img_no_sources`'s own raster is 640×479 — its exact width/height ratio, so `aspectRatio` reproduces it with zero letterboxing. */
private const val NEEDLE_ILLUSTRATION_ASPECT_RATIO = 640f / 479f

/**
 * The Stand's quick filter (owner: "filter... the globe with the segmented
 * circle and the topics with how many articles for each remain for the
 * selected region — it's a cascading filter"). This writes the exact same
 * standing [ReaderFilter] Contrast's region/topic chips do — it's just a
 * faster way to reach it from the Stand itself — but shows a live per-topic
 * count for whichever region is picked, so picking a region first narrows
 * what the topic row even offers (cascading, not two independent pickers).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RegionTopicFilterSheet(
    filter: ReaderFilter,
    topicCounts: Map<Topic, Int>,
    onSetRegion: (Region) -> Unit,
    onToggleTopic: (String) -> Unit,
    onClearFilter: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.sm)
                .padding(bottom = Tokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.list_filter), style = MaterialTheme.typography.titleLarge, color = ink, modifier = Modifier.weight(1f))
                if (filter.region != Region.GLOBAL || !filter.allTopics) {
                    TextButton(onClick = onClearFilter) { Text(stringResource(Res.string.list_clear)) }
                }
            }

            SectionHeading(stringResource(Res.string.list_region))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
            ) {
                for (region in Region.entries) {
                    val chosen = region == filter.region
                    Text(
                        region.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        color = if (chosen) ink else muted,
                        modifier = Modifier
                            .clickable(onClickLabel = stringResource(Res.string.list_filter_region, region.label)) { onSetRegion(region) }
                            .padding(vertical = Tokens.Spacing.xxs),
                    )
                }
            }

            SectionHeading(stringResource(Res.string.list_topics))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
            ) {
                for (topic in Topic.entries) {
                    val count = topicCounts[topic] ?: 0
                    val picked = topic.key in filter.topicKeys
                    val chosen = filter.allTopics || picked
                    Text(
                        stringResource(Res.string.list_topic_count, topic.placeholderLabel, count),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                        color = if (chosen) ink else muted,
                        modifier = Modifier
                            .clickable(
                                enabled = count > 0 || picked,
                                onClickLabel = stringResource(Res.string.list_toggle_topic, topic.placeholderLabel),
                            ) { onToggleTopic(topic.key) }
                            .padding(vertical = Tokens.Spacing.xxs),
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: Item,
    sourceTitle: String?,
    /** An excerpt of the article body around the search hit, when it matched there. */
    matchSnippet: String? = null,
    read: Boolean,
    readMarkStyle: ReadMarkStyle,
    showFeedImages: Boolean,
    hideNsfwImages: Boolean,
    imageStyle: ImageStyle,
    onClick: () -> Unit,
) {
    // A read article marks itself in place (owner's ask) — no separate icon,
    // just the title itself dimmed or struck through, per the reader's own
    // Settings choice.
    // Resolved outside the semantics block, which is not a composable scope.
    val readLabel = stringResource(Res.string.list_read)
    val unreadLabel = stringResource(Res.string.list_unread)
    val strike = read && readMarkStyle == ReadMarkStyle.STRIKETHROUGH
    val titleColor = if (read && readMarkStyle == ReadMarkStyle.GREYED) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // Read/unread is the app's central state, and until now it was
            // spent entirely on `strike` and `titleColor` above — a text
            // decoration and a colour. Neither reaches the semantics API, so a
            // read row and an unread row were byte-identical in speech, in an
            // app whose stated purpose is showing consumption. stateDescription
            // is the channel that carries it, and it costs one line.
            .semantics { stateDescription = if (read) readLabel else unreadLabel }
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
    ) {
        // The feed's own thumbnail (owner's ask), where it supplied one — see
        // FeedImage for the on/off, style, and source-declared-NSFW checks.
        if (showFeedImages) {
            FeedImage(
                imageUrl = item.imageUrl,
                declaredNsfw = item.declaredNsfw,
                hideNsfw = hideNsfwImages,
                style = imageStyle,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(Tokens.Radius.sm)),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
        ) {
            // Serif title — the Stand mock's voice (headlineSmall is serif by role).
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    textDecoration = if (strike) TextDecoration.LineThrough else null,
                ),
                color = titleColor,
                maxLines = 3,
            )
            Text(
                byline(item, sourceTitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (matchSnippet != null) {
                Text(
                    matchSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** "Author | Source · Jul 8, 14:02" — the mock's byline, plus recency (kept: honest and useful). */
private fun byline(item: Item, sourceTitle: String?): String {
    val time = TIME_FORMAT.format(java.time.Instant.ofEpochMilli(item.publishedAt).atZone(ZoneId.systemDefault()))
    val names = listOfNotNull(item.author?.takeIf { it.isNotBlank() }, sourceTitle).joinToString(" | ")
    return if (names.isEmpty()) time else "$names · $time"
}
