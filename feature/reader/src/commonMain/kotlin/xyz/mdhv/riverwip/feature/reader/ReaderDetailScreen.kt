package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.DayMixBar
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.paperGrain
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.design.res.*
import androidx.compose.foundation.layout.aspectRatio
import xyz.mdhv.riverwip.feature.lens.LensAnnotatedParagraph
import xyz.mdhv.riverwip.feature.lens.LensViewModel
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.ImageStyle
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.PaperGrain
import xyz.mdhv.riverwip.model.ReadingAsideStyle
import xyz.mdhv.riverwip.model.Topic
import kotlin.math.ceil

/** Static estimate from content length (~200 wpm). Display-only — nothing is measured or stored (brief §3). */
private fun readingMinutes(paragraphs: List<String>): Int {
    val words = paragraphs.sumOf { it.split(Regex("\\s+")).count { w -> w.isNotBlank() } }
    return ceil(words / 200.0).toInt().coerceAtLeast(1)
}

/**
 * The end-of-article marker (brief §3: "the river has banks"), Previous/Next
 * flanking it so reaching the end doesn't dead-end (owner's ask). Shared by
 * [ReaderDetailScreen] and [NewspaperReaderPane]. Equal-width side slots keep
 * the label centred in the row regardless of whether either side has a target
 * — a present-but-unclickable label would misrepresent a real edge of the
 * list as just a quiet moment, so an absent neighbour renders nothing at all.
 */
@Composable
fun EndOfArticleRow(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (hasPrevious) {
                Text(
                    stringResource(Res.string.reader_previous),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onPrevious),
                )
            }
        }
        Text(
            stringResource(Res.string.reader_end_of_article),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (hasNext) {
                Text(
                    stringResource(Res.string.reader_next),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onNext),
                )
            }
        }
    }
}

/**
 * The immersive paper (owner's Paper mock + flow map). The article is a rigid
 * sheet that follows a one-finger horizontal drag and slides off with a crisp,
 * still edge (a drop-shadow that stays sharp as it moves): drag right past the
 * threshold to return to the stand, left for settings, otherwise it snaps back.
 * A two-finger vertical drag slides brightness; a two-finger flick steps the
 * theme (see [readerGestures]). The controls float over a gradient fade of the
 * text — no strip — the way the mock's bottom line does: reading progress, open
 * in browser, share, estimated time, and the day bar (which opens the loom).
 */
@Composable
fun ReaderDetailScreen(
    vm: ReaderViewModel,
    lensVm: LensViewModel,
    item: Item,
    showReadingTime: Boolean,
    lensOn: Boolean,
    saved: Boolean,
    immersive: Boolean,
    paperGrain: PaperGrain,
    showFeedImages: Boolean,
    hideNsfwImages: Boolean,
    imageStyle: ImageStyle,
    readingAsideStyle: ReadingAsideStyle,
    offsetX: Float,
    // 0f = full Paper, 1f = fully parked — the one value driving scale, shadow,
    // corner radius and (together with offsetX's sign) translation, read live
    // during the drag and only eased by the caller's settle animation after
    // release (owner's brief, 2026-07: never animate() the drag itself).
    progress: Float,
    parkedRoom: ReaderRoom?,
    onToggleLens: () -> Unit,
    onToggleClip: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRoomDragStart: (ReaderRoom) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (velocityX: Float) -> Unit,
    onParkedTap: () -> Unit,
    onOpenLoom: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onThemeFlick: () -> Unit,
) {
    val state by vm.articleState.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
    // The reader's bottom bar is the reader's *own* today mix — what they've
    // read today (owner), the same read-distribution the Stand's top bar
    // shows, not the ambient supply mix. Tapping it still opens the loom.
    val todayMix by vm.todayReadMix.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val sharer = rememberClippingSharer()
    val topic = Classifier.dominantTopic(item.topics)
    val background = MaterialTheme.colorScheme.background

    // End-of-article Previous/Next (owner's ask): the item adjacent to this one
    // in the *current* list ordering, the same one ArticleListScreen shows.
    val listItems by vm.items.collectAsStateWithLifecycle()
    val currentIndex = listItems.indexOfFirst { it.id == item.id }
    val previousItem = if (currentIndex > 0) listItems.getOrNull(currentIndex - 1) else null
    val nextItem = if (currentIndex != -1) listItems.getOrNull(currentIndex + 1) else null

    val listState = rememberLazyListState()
    // A Previous/Next tap swaps `item` under this same composable instance —
    // without this, the LazyColumn would keep whatever scroll offset the
    // reader was at (the very bottom, having just reached the end) and open
    // the new article already scrolled past its own start.
    LaunchedEffect(item.id) { listState.scrollToItem(0) }

    val readingAside by vm.readingAside.collectAsStateWithLifecycle()
    // The reading-aside clock only counts while this screen is actually the
    // one resumed and on screen -- backgrounding the app or navigating away
    // (both move this lifecycle off RESUMED) pauses it, same as the web
    // reader's own tab-visibility gate.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            vm.setReaderActive(event == Lifecycle.Event.ON_RESUME)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.setReaderActive(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX
                    // Lift-and-part: Paper shrinks, gains a shadow and rounds
                    // its corners together, all read directly off `progress`
                    // — the same value the drag is driving, never a tween.
                    val scale = 1f - progress * (1f - PAPER_MIN_SCALE)
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = progress * PAPER_MAX_SHADOW_DP.dp.toPx()
                    shape = RoundedCornerShape((progress * PAPER_MAX_CORNER_DP).dp)
                    clip = true
                }
                .background(background)
                .paperGrain(paperGrain, MaterialTheme.colorScheme.onBackground)
                .readerGestures(
                    parkedRoom = parkedRoom,
                    onRoomDragStart = onRoomDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onParkedTap = onParkedTap,
                    onBrightnessDelta = onBrightnessDelta,
                    onThemeFlick = onThemeFlick,
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Tokens.Spacing.md,
                    end = Tokens.Spacing.md,
                    top = Tokens.Spacing.xxl,
                    // Leave room for the floating controls + their fade.
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                item {
                    // Owner's Paper mock: the body begins at the screen's centre,
                    // with the title sitting just above it — so the header block
                    // fills the top ~half and its content bottoms out at centre.
                    Column(
                        modifier = Modifier.fillParentMaxHeight(0.48f),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs, Alignment.Bottom),
                    ) {
                        // Colour never alone: the dominant topic, named.
                        Text(
                            topic.placeholderLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = topic.toComposeColor(),
                        )
                        Text(item.title, style = MaterialTheme.typography.displayLarge)
                        val author = item.author
                        val sourceTitle = sourceTitles[item.sourceId]
                        // Source left, author right — but real bylines run long
                        // (a live blog can list four names). Each takes half the
                        // width and wraps/ellipsises within it, so a long author
                        // never collides with the source (owner: alignment bug).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                sourceTitle ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!author.isNullOrBlank()) {
                                Text(
                                    author,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                // The feed's own hero image (owner's ask), where it supplied
                // one — right under the headline block, above the body.
                if (showFeedImages) {
                    item {
                        FeedImage(
                            imageUrl = item.imageUrl,
                            declaredNsfw = item.declaredNsfw,
                            hideNsfw = hideNsfwImages,
                            style = imageStyle,
                            modifier = Modifier
                                .padding(bottom = Tokens.Spacing.md)
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(Tokens.Radius.md)),
                        )
                    }
                }
                when (val s = state) {
                    is ArticleUiState.Loading -> item {
                        val loading = stringResource(Res.string.reader_loading)
                        Box(Modifier.fillMaxWidth().padding(top = Tokens.Spacing.xxl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.semantics {
                                    contentDescription = loading
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                    }
                    is ArticleUiState.Loaded -> {
                        // The aside breaks up roughly the middle of the piece,
                        // a stable, deterministic spot -- the way a real
                        // pull-quote sets one off mid-column, not at a random
                        // scroll position that would jump around on recompose.
                        val asideAt = readingAside
                            ?.takeIf { it.itemId == item.id }
                            ?.let { s.paragraphs.size / 2 }
                        itemsIndexed(s.paragraphs) { index, para ->
                            Column {
                                LensAnnotatedParagraph(
                                    itemId = item.id,
                                    text = para,
                                    vm = lensVm,
                                    style = MaterialTheme.typography.bodyLarge,
                                    // A touch more room than the list's base spacedBy
                                    // gap, stacked on top of it: at just Tokens.Spacing.md
                                    // between every item, paragraph breaks read as
                                    // barely more than an extra line — this widens the
                                    // gap specifically between paragraphs so they read
                                    // as distinct blocks (owner's #2, rendering quality).
                                    modifier = Modifier.padding(bottom = Tokens.Spacing.xs),
                                )
                                if (index == asideAt) {
                                    FoundQuoteAside(
                                        readingAside!!,
                                        readingAsideStyle,
                                        modifier = Modifier.padding(top = Tokens.Spacing.xs, bottom = Tokens.Spacing.sm),
                                    )
                                }
                            }
                        }
                    }
                    is ArticleUiState.Fallback -> item {
                        // Some feeds (aggregators like Google News especially)
                        // only carry a summary, or link to a redirect page we
                        // can't read through. Present what we have with dignity
                        // — the summary as the body, then a plain invitation to
                        // finish at the source — never an apologetic error line
                        // (owner: stubs read as broken).
                        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                            Text(
                                s.summary?.takeIf { it.isNotBlank() }
                                    ?: "This source shares only a short summary in its feed.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(Res.string.reader_full_story),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .clickable {
                                        uriHandler.openUri(item.canonicalUrl)
                                    }
                                    .padding(top = Tokens.Spacing.xs),
                            )
                        }
                    }
                }
                item {
                    // The river has banks (brief §3): the article itself has an
                    // explicit end too — flanked by Previous/Next so reaching it
                    // doesn't dead-end (owner's ask).
                    EndOfArticleRow(
                        hasPrevious = previousItem != null,
                        hasNext = nextItem != null,
                        onPrevious = { previousItem?.let { vm.openItem(it) } },
                        onNext = { nextItem?.let { vm.openItem(it) } },
                        modifier = Modifier.padding(top = Tokens.Spacing.lg),
                    )
                }
            }

            // The bottom line: controls floating over a gradient fade of the text.
            val readingProgress by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    if (total == 0) 0f
                    else (((info.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1f) / total).coerceIn(0f, 1f)
                }
            }
            ReaderUtilityBar(
                state = state,
                showReadingTime = showReadingTime,
                todayMix = todayMix,
                progress = readingProgress,
                background = background,
                lensOn = lensOn,
                saved = saved,
                onToggleLens = onToggleLens,
                onToggleClip = onToggleClip,
                onOpenBrowser = {
                    uriHandler.openUri(item.canonicalUrl)
                },
                onShare = {
                    sharer.share(
                        title = item.title,
                        source = sourceTitles[item.sourceId],
                        author = item.author,
                        url = item.canonicalUrl,
                    )
                },
                onOpenLoom = onOpenLoom,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // Owner's #22: an obvious back control, on by default. Immersive mode
            // hides it (and everything else fades into the gestures-only page).
            // Sat directly over the scrolling text with nothing behind it, the
            // arrow intersected whatever paragraph happened to scroll under it
            // and the text itself cut off at a hard rectangular edge (owner's
            // report) — the same gradient-fade-of-the-text treatment
            // [ReaderUtilityBar] already uses at the bottom, just inverted top
            // to bottom, keeps the control legible without ever hard-clipping
            // the text beneath it. The settings control mirrors it on the other
            // side (owner: back is left, so settings should be its mirror on
            // the right) — the same drag this scrim's left edge triggers
            // (right → stand, left → settings) is now reachable without a
            // gesture either way.
            if (!immersive) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(Brush.verticalGradient(listOf(background, background, Color.Transparent))),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Tokens.Spacing.xs),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.reader_back_to_stand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Tokens.Spacing.xs),
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(Res.string.reader_open_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bottom utility bar — shared with [NewspaperReaderPane], the tablet/
 * large-screen multi-column layout, which has no [LazyListState] of its own
 * (it's one long scrolling `Column`, not item-indexed), so `progress` is
 * whatever the caller's own scroll position works out to be, not something
 * this bar derives itself.
 */
@Composable
internal fun ReaderUtilityBar(
    state: ArticleUiState,
    showReadingTime: Boolean,
    todayMix: List<Pair<Topic, Double>>,
    progress: Float,
    background: Color,
    lensOn: Boolean,
    saved: Boolean,
    onToggleLens: () -> Unit,
    onToggleClip: () -> Unit,
    onOpenBrowser: () -> Unit,
    onShare: () -> Unit,
    onOpenLoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, background, background))),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.sm, vertical = Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressDial(progress = progress)
            // The lens: an unmistakable eye — bold ink when on, dimmed when off
            // (never the crossed-out incognito look). Toggles the loaded-language
            // highlighting, the same persisted setting Settings shows.
            IconButton(
                onClick = onToggleLens,
                modifier = Modifier.semantics { stateDescription = if (lensOn) "On" else "Off" },
            ) {
                // Non-colour channel: a filled pill behind the eye when on.
                Box(
                    modifier = if (lensOn) {
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.RemoveRedEye,
                        contentDescription = stringResource(Res.string.reader_lens_toggle),
                        tint = if (lensOn) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.padding(Tokens.Spacing.xxs),
                    )
                }
            }
            IconButton(onClick = onOpenBrowser) {
                Icon(Icons.Filled.Public, contentDescription = stringResource(Res.string.reader_open_in_browser))
            }
            IconButton(onClick = onToggleClip) {
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (saved) "Remove clipping" else "Save as a clipping",
                    tint = if (saved) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(Res.string.reader_share_clipping))
            }
            val s = state
            // Listen to the article itself (owner: "where is the play button to
            // hear the article?") — the device's own on-device text-to-speech,
            // same engine Nooz Flash's own Play uses, reading the full loaded
            // body rather than a compressed line. Only once real text has
            // loaded; a fallback summary-only state has nothing worth reading
            // aloud beyond what's already on screen.
            if (s is ArticleUiState.Loaded) {
                PlayTextButton(
                    text = remember(s.paragraphs) { s.paragraphs.joinToString("\n\n") },
                    playLabel = "Read the article aloud",
                )
            }
            if (showReadingTime && s is ArticleUiState.Loaded) {
                val minutes = remember(s.paragraphs) { readingMinutes(s.paragraphs) }
                val readingTime = stringResource(Res.string.reader_reading_time, minutes)
                Text(
                    stringResource(Res.string.reader_minutes_short, minutes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = Tokens.Spacing.sm)
                        .semantics { contentDescription = readingTime },
                )
            } else {
                Spacer(Modifier.width(Tokens.Spacing.sm))
            }
            if (todayMix.isNotEmpty()) {
                // 6dp bar, but a 48dp tap target (the bar is centred inside it).
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = stringResource(Res.string.list_open_loom)) { onOpenLoom() },
                    contentAlignment = Alignment.Center,
                ) {
                    DayMixBar(todayMix, Modifier.fillMaxWidth())
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The mock's small reading-progress ring: a faint track with a sweep from the top. */
@Composable
private fun ProgressDial(progress: Float) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val pct = (progress * 100).toInt()
    val spokenProgress = stringResource(Res.string.reader_progress, pct)
    Canvas(
        Modifier
            .padding(start = Tokens.Spacing.xs, end = Tokens.Spacing.xxs)
            .size(20.dp)
            .semantics { contentDescription = spokenProgress },
    ) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke / 2f
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke),
        )
        drawArc(
            color = ink,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
