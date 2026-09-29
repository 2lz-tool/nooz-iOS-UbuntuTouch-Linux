package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.paperGrain
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.model.AffectSpanDetector
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.ImageStyle
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.PaperGrain

/** Comfortable reading measure per column — narrower and it starts feeling like a phone column squeezed sideways. */
private val IDEAL_COLUMN_WIDTH = 340.dp
private val COLUMN_GUTTER = 32.dp
private const val MAX_COLUMNS = 3

/**
 * How many newspaper columns fit at this pane width, or null if it's not
 * wide enough for at least two — the "or a combination based on the space"
 * case, where [ReaderScreenTwoPane] falls back to the plain single-column
 * [ReaderDetailScreen] instead of this layout.
 */
fun columnsForWidth(paneWidth: Dp): Int? {
    val fit = ((paneWidth + COLUMN_GUTTER) / (IDEAL_COLUMN_WIDTH + COLUMN_GUTTER)).toInt().coerceAtMost(MAX_COLUMNS)
    return fit.takeIf { it >= 2 }
}

/**
 * The newspaper-column reading layout (owner: "the reading panel structured
 * into the columns of text as newspapers do, with the first becoming a
 * headline... above the fold, below the fold"). Only used once
 * [columnsForWidth] says the pane is wide enough — the masthead-style
 * headline sits full-width at the top, and the body is chunked into
 * fixed-height "pages" of [columns] side-by-side columns, filled in true
 * reading order (all of column 1 top-to-bottom, then column 2, ...) rather
 * than several columns scrolling in lockstep. Each page is one "fold" —
 * scrolling from one to the next is turning the page.
 *
 * Known gap: only the lens's *passive* mark carries over here — the subtle
 * loaded-language underline (see [NewspaperColumns]), gated by the same
 * "Highlight loaded language" setting ([lensOn]) as everywhere else. The
 * *interactive* half ([ReaderDetailScreen]'s own per-paragraph
 * `LensAnnotatedParagraph`: tap-to-define, tap-to-defuse/accept-rewrite) does
 * not — that needs a tap's screen position remapped to a character offset
 * *within whichever column/page slice it landed in*, which is a materially
 * riskier separate piece of work than drawing a mark. It also means a span
 * defused or accepted in [ReaderDetailScreen] shows no trace here (this pane
 * keeps no per-span override state) — switching layouts on the same article
 * can show it underlined again.
 */
@Composable
fun NewspaperReaderPane(
    vm: ReaderViewModel,
    item: Item,
    showReadingTime: Boolean,
    lensOn: Boolean,
    lensDisabledDefaultTerms: Set<String>,
    lensCustomTerms: Set<String>,
    saved: Boolean,
    paperGrain: PaperGrain,
    showFeedImages: Boolean,
    hideNsfwImages: Boolean,
    imageStyle: ImageStyle,
    columns: Int,
    onToggleLens: () -> Unit,
    onToggleClip: () -> Unit,
    onOpenLoom: () -> Unit,
) {
    val state by vm.articleState.collectAsStateWithLifecycle()
    val sourceTitles by vm.sourceTitles.collectAsStateWithLifecycle()
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

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(background)
            .paperGrain(paperGrain, MaterialTheme.colorScheme.onBackground),
    ) {
        // The true viewport, measured here — outside the scroll below, which
        // would otherwise report unbounded height. Pages budget off this, not
        // off the headline's actual (variable) height, so every page after
        // the first is a plain, predictable full page of columns; the first
        // is simply whatever's left under the headline once it's drawn.
        val pageHeight = (maxHeight - 120.dp).coerceAtLeast(200.dp)
        val columnsWidth = maxWidth - Tokens.Spacing.xl * 2
        val columnWidth = ((columnsWidth + COLUMN_GUTTER) / columns) - COLUMN_GUTTER

        val scrollState = rememberScrollState()
        // A Previous/Next tap swaps `item` under this same composable instance —
        // without this, the Column would keep whatever scroll offset the reader
        // was at (the very bottom, having just reached the end) and open the
        // new article already scrolled past its own start.
        LaunchedEffect(item.id) { scrollState.scrollTo(0) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Tokens.Spacing.xl, vertical = Tokens.Spacing.xxl),
        ) {
            // The masthead-style headline (owner: "the first becoming a
            // headline") — full width, above the columns, with a rule
            // beneath it like a real front page.
            Text(topic.placeholderLabel, style = MaterialTheme.typography.labelLarge, color = topic.toComposeColor())
            Spacer(Modifier.height(Tokens.Spacing.xs))
            Text(item.title, style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(Tokens.Spacing.sm))
            val author = item.author
            val sourceTitle = sourceTitles[item.sourceId]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    sourceTitle ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!author.isNullOrBlank()) {
                    Text(
                        author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // The feed's own masthead photo (owner's ask), where it supplied
            // one — under the byline, above the rule, like a real front page.
            if (showFeedImages) {
                Spacer(Modifier.height(Tokens.Spacing.md))
                FeedImage(
                    imageUrl = item.imageUrl,
                    declaredNsfw = item.declaredNsfw,
                    hideNsfw = hideNsfwImages,
                    style = imageStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(21f / 9f)
                        .clip(RoundedCornerShape(Tokens.Radius.md)),
                )
            }
            Spacer(Modifier.height(Tokens.Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Tokens.Spacing.lg))

            when (val s = state) {
                is ArticleUiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(top = Tokens.Spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    // Hoisted: a semantics block is not a composable scope.
                    val loadingLabel = stringResource(Res.string.reader_loading)
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = loadingLabel
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                is ArticleUiState.Loaded -> NewspaperColumns(
                    paragraphs = s.paragraphs,
                    columns = columns,
                    columnWidth = columnWidth,
                    pageHeight = pageHeight,
                    bodyStyle = MaterialTheme.typography.bodyLarge,
                    lensOn = lensOn,
                    lensDisabledDefaultTerms = lensDisabledDefaultTerms,
                    lensCustomTerms = lensCustomTerms,
                )
                is ArticleUiState.Fallback -> Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
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
            Spacer(Modifier.height(Tokens.Spacing.lg))
            EndOfArticleRow(
                hasPrevious = previousItem != null,
                hasNext = nextItem != null,
                onPrevious = { previousItem?.let { vm.openItem(it) } },
                onNext = { nextItem?.let { vm.openItem(it) } },
            )
            Spacer(Modifier.height(96.dp))
        }

        val readingProgress by remember {
            derivedStateOf {
                if (scrollState.maxValue == 0) 0f else (scrollState.value.toFloat() / scrollState.maxValue).coerceIn(0f, 1f)
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
    }
}

/**
 * The body text, chunked into fixed-height "columns" in true reading order
 * (column 1 top-to-bottom, then column 2, ...) and grouped into pages of
 * [columns] each. Measures the whole body once at the column width to get
 * real line breaks (`TextMeasurer`), then slices by line index — never mid-
 * line — so each rendered slice re-wraps to exactly the lines it was cut at.
 *
 * Carries over the *passive* half of the reading lens (brief §P5): when
 * [lensOn], each paragraph is run through the same
 * [AffectSpanDetector.detect] the single-column `LensAnnotatedParagraph`
 * uses (pure, synchronous, no ViewModel needed), and each detected span's
 * offsets are carried into [fullText]'s own coordinate space alongside it.
 * A column slice can land mid-span — that's the whole reason this is
 * non-trivial — so the underline for a given column is drawn from the
 * *shared* whole-body [layout], clipped to that column's [start, end), never
 * from a fresh per-column measurement: the wrapping is already known
 * identical (same text, width, style), so its line geometry is reused
 * outright, just shifted up by that column's own first line's top. No
 * character-offset-to-tap remapping is attempted — see the gap called out
 * on [NewspaperReaderPane] itself.
 */
@Composable
private fun NewspaperColumns(
    paragraphs: List<String>,
    columns: Int,
    columnWidth: Dp,
    pageHeight: Dp,
    bodyStyle: TextStyle,
    lensOn: Boolean,
    lensDisabledDefaultTerms: Set<String>,
    lensCustomTerms: Set<String>,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // A blank line between paragraphs — simpler and safer to reason about
    // than reproducing true newsprint's indented, gapless paragraph marks,
    // and it re-wraps identically wherever a column slice lands inside it.
    // Loaded-language spans are detected per paragraph (their offsets are
    // paragraph-local) and rebased onto this joined text's own offsets so
    // they line up with the same line-chunking below. Advanced settings'
    // per-word disable/custom-term customization applies here exactly as it
    // does in the single-column LensAnnotatedParagraph (same detector, same
    // two parameters), so turning off a default word or adding a custom one
    // is respected in both reading layouts identically.
    val (fullText, affectSpans) = remember(paragraphs, lensOn, lensDisabledDefaultTerms, lensCustomTerms) {
        val sb = StringBuilder()
        val spans = mutableListOf<IntRange>()
        paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) sb.append("\n\n")
            val paragraphStart = sb.length
            sb.append(paragraph)
            if (lensOn) {
                for (span in AffectSpanDetector.detect(paragraph, lensDisabledDefaultTerms, lensCustomTerms)) {
                    spans.add((paragraphStart + span.start) until (paragraphStart + span.end))
                }
            }
        }
        sb.toString() to spans
    }

    val columnWidthPx = with(density) { columnWidth.roundToPx() }.coerceAtLeast(1)
    val pageHeightPx = with(density) { pageHeight.roundToPx() }.coerceAtLeast(1)
    val underlineStroke = with(density) { 1.3.dp.toPx() }

    val layout = remember(fullText, bodyStyle, columnWidthPx) {
        textMeasurer.measure(text = fullText, style = bodyStyle, constraints = Constraints(maxWidth = columnWidthPx))
    }

    val columnRanges = remember(layout, pageHeightPx) {
        buildList {
            var line = 0
            val total = layout.lineCount
            while (line < total) {
                val top = layout.getLineTop(line)
                var end = line
                while (end < total && layout.getLineBottom(end) - top <= pageHeightPx) end++
                if (end == line) end++ // always progress, even if a single line alone overflows the page
                add(layout.getLineStart(line) to layout.getLineEnd(end - 1))
                line = end
            }
        }
    }
    val pages = remember(columnRanges, columns) { columnRanges.chunked(columns) }

    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxl)) {
        for (page in pages) {
            Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GUTTER)) {
                for ((start, end) in page) {
                    // This column's loaded-language marks, clipped to
                    // [start, end) — offsets stay GLOBAL (into `layout`,
                    // not this column's substring), since drawSpanUnderline
                    // reads geometry straight off the shared whole-body layout.
                    val underlines = affectSpans.mapNotNull { span ->
                        val lo = maxOf(span.first, start)
                        val hi = minOf(span.last + 1, end)
                        if (hi > lo) lo to hi else null
                    }
                    val columnTop = if (underlines.isEmpty()) {
                        0f
                    } else {
                        layout.getLineTop(layout.getLineForOffset(start))
                    }
                    Text(
                        text = fullText.substring(start, end),
                        style = bodyStyle,
                        modifier = Modifier
                            .width(columnWidth)
                            .height(pageHeight)
                            .drawBehind {
                                for ((lo, hi) in underlines) {
                                    drawSpanUnderline(layout, lo, hi, columnTop, LoadedUnderline, underlineStroke)
                                }
                            },
                    )
                }
            }
        }
    }
}

// Same visual mark as the single-column lens (LensAnnotatedParagraph's own
// LoadedUnderline, in :feature:lens) — a muted red underline drawn behind
// otherwise-normal ink, never coloured link-blue text. Kept as a literal
// copy here rather than a cross-module export: this pane only ever wants the
// passive mark, never the interactive state (tap targets, defuse/accept
// overrides) that lives alongside it in that file.
private val LoadedUnderline = Color(0xFFC0442F).copy(alpha = 0.6f)

/**
 * Draw the loaded-language underline for GLOBAL offsets [start, end) — into
 * the shared whole-body [layout], the same one already used to slice lines
 * into columns — line by line, shifted up by [columnTop] (that column's own
 * first line's top in the shared layout) so it lands correctly within this
 * column's own local drawing space. Mirrors LensAnnotatedParagraph's
 * drawSpanUnderline (not shared code: that one draws from a per-paragraph,
 * locally-measured layout captured via `onTextLayout`, since a single
 * paragraph's `Text` there can't reuse a whole-body layout the way a column
 * here can).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpanUnderline(
    layout: TextLayoutResult,
    start: Int,
    end: Int,
    columnTop: Float,
    color: Color,
    stroke: Float,
) {
    if (end <= start) return
    val last = (end - 1).coerceAtLeast(start)
    val startLine = layout.getLineForOffset(start)
    val endLine = layout.getLineForOffset(last)
    for (line in startLine..endLine) {
        val ls = maxOf(start, layout.getLineStart(line))
        val le = minOf(end, layout.getLineEnd(line, visibleEnd = true))
        if (le <= ls) continue
        val x1 = layout.getHorizontalPosition(ls, usePrimaryDirection = true)
        val x2 = layout.getHorizontalPosition(le, usePrimaryDirection = true)
        val y = layout.getLineBottom(line) - columnTop - stroke * 2f
        drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = stroke)
    }
}
