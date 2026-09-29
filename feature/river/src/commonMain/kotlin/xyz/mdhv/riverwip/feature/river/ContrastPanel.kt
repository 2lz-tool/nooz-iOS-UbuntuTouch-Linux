package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.EmptyState
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.model.GlobeModel
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.Topic
import kotlin.math.roundToInt

/** How the contrast rows are ordered — the reader picks what the view is "about" (owner: configurable). */
private enum class ContrastSort(val label: String) {
    FLOWED("Flowed"),
    READ("Read"),
    GAP("Gap"),
}

private data class ContrastRow(
    val topic: Topic,
    val flowed: Int,
    val read: Int,
    val flowedShare: Float,
    val readShare: Float,
) {
    /** Positive = a blind spot (more flowed than you read); negative = a fixation (read more than flowed). */
    val gap: Float get() = flowedShare - readShare
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

/**
 * The omission dashboard (owner's contrast idea) — the loom's stark counterpart,
 * redrawn minimal (owner's references): one muted ink, thin marks, wide
 * whitespace, numbers doing the talking. Two contrasts, top to bottom:
 *
 *  - **Reach**: a single nested bar funnelling everything that **flowed** →
 *    what your **filter** let through → what you **read**, each a deeper shade
 *    of the one ink, so the two omissions (the one you chose, the one your
 *    attention made) are the shrinking of the bar.
 *  - **By topic**: a dumbbell per topic — a faint dot for its share of the
 *    stream, a solid dot for its share of your reading, a hairline between
 *    them. The length of that line is the gap.
 *
 * Supply is never filtered away here — omission is the subject.
 */
@Composable
fun ContrastPanel(
    streamByTopic: Map<String, Int>,
    readByTopic: Map<String, Int>,
    sourceCounts: Map<String, Int>,
    filter: ReaderFilter,
    enabledSourceCount: Int,
    readsByRegion: Map<Region, Int>,
    onSetRegion: (Region) -> Unit,
    onToggleTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalFlowed = streamByTopic.values.sum()
    val totalRead = readByTopic.values.sum()
    val admitted = if (filter.allTopics) {
        totalFlowed
    } else {
        streamByTopic.entries.filter { filter.matchesTopic(Topic.fromKey(it.key)) }.sumOf { it.value }
    }
    val setAside = (totalFlowed - admitted).coerceAtLeast(0)
    val delivered = sourceCounts.count { it.value > 0 }
    val silent = (enabledSourceCount - delivered).coerceAtLeast(0)

    val rows = remember(streamByTopic, readByTopic) {
        val keys = streamByTopic.keys + readByTopic.keys
        keys.map { key ->
            val flowed = streamByTopic[key] ?: 0
            val read = readByTopic[key] ?: 0
            ContrastRow(
                topic = Topic.fromKey(key),
                flowed = flowed,
                read = read,
                flowedShare = if (totalFlowed == 0) 0f else flowed.toFloat() / totalFlowed,
                readShare = if (totalRead == 0) 0f else read.toFloat() / totalRead,
            )
        }
    }

    var sort by remember { mutableStateOf(ContrastSort.FLOWED) }
    val ordered = remember(rows, sort) {
        when (sort) {
            ContrastSort.FLOWED -> rows.sortedByDescending { it.flowed }
            ContrastSort.READ -> rows.sortedByDescending { it.read }
            ContrastSort.GAP -> rows.sortedByDescending { it.gap }
        }
    }
    val maxShare = remember(rows) {
        rows.maxOfOrNull { maxOf(it.flowedShare, it.readShare) }?.takeIf { it > 0f } ?: 1f
    }

    val ink = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xl),
    ) {
        // ---- Regions: the globe opened up as a read-heatmap + the filter ----
        RegionsSection(
            reads = readsByRegion,
            filter = filter,
            onSetRegion = onSetRegion,
            onToggleTopic = onToggleTopic,
            ink = ink,
            muted = muted,
        )

        if (totalFlowed == 0) {
            EmptyState(
                title = stringResource(Res.string.contrast_nothing_flowed),
                body = "The contrast, what your sources ran against what you read, appears once anything comes in.",
                fill = false,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        // ---- Reach: one nested funnel bar + a spare legend ----
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
            SectionHeading(stringResource(Res.string.contrast_reach))
            NestedFunnel(
                filterFraction = admitted.toFloat() / totalFlowed,
                readFraction = totalRead.toFloat() / totalFlowed,
                ink = ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.lg)) {
                FunnelLegend("Flowed", totalFlowed, ink.copy(alpha = 0.22f), ink)
                FunnelLegend("Filter", admitted, ink.copy(alpha = 0.5f), ink)
                FunnelLegend("Read", totalRead, ink, ink)
            }
            val sentences = buildList {
                if (setAside > 0) add("Your filter set aside $setAside ${plural(setAside, "story", "stories")} before they reached you.")
                if (admitted > 0) add("You read $totalRead of the $admitted it let through.")
                add(
                    buildString {
                        append("$enabledSourceCount ${plural(enabledSourceCount, "source", "sources")} on · $delivered delivered")
                        if (silent > 0) append(" · $silent quiet")
                    },
                )
            }
            for (line in sentences) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = muted)
            }
        }

        // ---- By topic: dumbbells + a spare sort lever ----
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading(stringResource(Res.string.contrast_by_topic), modifier = Modifier.weight(1f))
                Row(
                    Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                ) {
                    for (option in ContrastSort.entries) {
                        val chosen = sort == option
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                            color = if (chosen) ink else muted,
                            modifier = Modifier
                                .selectable(selected = chosen, role = Role.RadioButton, onClick = { sort = option })
                                .padding(vertical = Tokens.Spacing.xxs),
                        )
                    }
                }
            }
            val blindSpot = ordered.maxByOrNull { it.gap }
            if (totalRead > 0 && blindSpot != null && blindSpot.gap > 0.08f) {
                Text(
                    stringResource(Res.string.contrast_widest_gap, blindSpot.topic.placeholderLabel, (blindSpot.flowedShare * 100).roundToInt(), (blindSpot.readShare * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
            for (row in ordered) {
                DumbbellRow(row = row, maxShare = maxShare, ink = ink, muted = muted)
            }
            // A whisper of a legend for the two dots.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Tokens.Spacing.xs),
            ) {
                DotKey(filled = false, label = stringResource(Res.string.contrast_flowed), ink = ink, muted = muted)
                DotKey(filled = true, label = stringResource(Res.string.contrast_read), ink = ink, muted = muted)
            }
        }
    }
}

/**
 * The globe opened up (owner #6b): the same landmass dots [GlobeCanvas] spins
 * as a sphere, laid flat instead — no spinning, no gesture, just the map
 * unrolled — each dot shaded by how much of your reading came from its own
 * region. (An earlier pass here tried the spinnable 3D globe instead; the
 * owner clarified that wasn't the ask — "I don't need it spinning, I want it
 * unwrapped." A later pass drew plain shaded rectangles per sector instead of
 * the dots themselves — owner: "it just shows rectangles, that won't do" — so
 * this reads it back off [GlobeModel.dots] directly, the same data the globe
 * draws from.) Below it, the region and topic filter, moved here from Edit
 * (owner #3): tap a region to aim there, tap topics to narrow.
 */
@Composable
private fun RegionsSection(
    reads: Map<Region, Int>,
    filter: ReaderFilter,
    onSetRegion: (Region) -> Unit,
    onToggleTopic: (String) -> Unit,
    ink: Color,
    muted: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
        SectionHeading(stringResource(Res.string.contrast_regions))
        RegionHeatStrip(reads = reads, selected = filter.region, ink = ink)
        if (reads.values.sum() == 0) {
            Text(
                stringResource(Res.string.contrast_no_reads),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        RegionChips(selected = filter.region, onSet = onSetRegion, ink = ink, muted = muted)
        TopicChips(topicKeys = filter.topicKeys, onToggle = onToggleTopic, ink = ink, muted = muted)
    }
}

/**
 * The read-by-region heatmap: [GlobeModel.dots] — the exact same 6°-grid
 * landmass dots [GlobeCanvas] projects onto a sphere — plotted instead onto a
 * flat equirectangular strip (lon → x, lat → y), so this reads as the globe
 * unrolled rather than an abstract row of shaded rectangles. Each dot's own
 * sector is resolved with the same [Region.forLongitude] every other picker
 * uses, so the antimeridian-wrapping Australia & Pacific sector needs no
 * special-casing here — a dot's longitude alone decides its region and thus
 * its shade. Always drawn with a full outline and sector dividers — regardless
 * of how faint the shading is at low read counts — so the strip reads as a
 * real control, not a rendering glitch (an earlier version's near-zero-alpha
 * fill at zero reads looked like nothing was there).
 */
// internal rather than private so ContrastAccessibilityTest can compose it on
// its own: the assertion is about what this one node publishes, and hoisting
// the whole panel to reach it would test the panel instead.
@Composable
internal fun RegionHeatStrip(reads: Map<Region, Int>, selected: Region, ink: Color) {
    val maxV = reads.entries.filter { it.key != Region.GLOBAL }.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1
    // A bare Canvas publishes nothing, so this map — which is the entire answer
    // to "where in the world have I been reading?" — was silent. Shade alone
    // carried the whole comparison, which is also unreadable for anyone who
    // cannot separate two close greys.
    val spoken = describeHeatStrip(reads, selected)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(360f / 168f)
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .semantics { contentDescription = spoken },
    ) {
        val w = size.width
        val h = size.height
        val dotRadius = 1.6.dp.toPx()
        fun x(lon: Double) = (((lon + 180.0) / 360.0) * w).toFloat()
        fun y(lat: Double) = (((84.0 - lat) / 168.0) * h).toFloat()
        fun outline(fromLon: Double, toLon: Double) {
            val left = x(fromLon)
            val right = x(toLon)
            drawRect(ink.copy(alpha = 0.75f), topLeft = Offset(left, 0f), size = Size((right - left).coerceAtLeast(2f), h), style = Stroke(1.5.dp.toPx()))
        }

        // Ocean: a faint fill so the strip reads as a map, not empty space.
        drawRect(ink.copy(alpha = 0.05f), size = size)

        // Sector dividers, one per region's own left edge (Australia &
        // Pacific's wrap sliver shares Americas' left edge, -170°, so no
        // separate divider is needed for it).
        for (r in Region.entries) {
            if (r == Region.GLOBAL) continue
            drawLine(ink.copy(alpha = 0.22f), Offset(x(r.fromLon), 0f), Offset(x(r.fromLon), h), strokeWidth = 1.dp.toPx())
        }

        // The land, dot by dot — shaded by its own sector's read volume,
        // dimmed further outside the aimed sector.
        for (dot in GlobeModel.dots) {
            val region = Region.forLongitude(dot[0])
            val v = reads[region] ?: 0
            val heat = if (v == 0) 0.3f else 0.4f + 0.5f * (v.toFloat() / maxV)
            val alpha = if (selected == Region.GLOBAL || region == selected) heat else heat * 0.35f
            drawCircle(ink.copy(alpha = alpha), radius = dotRadius, center = Offset(x(dot[0]), y(dot[1])))
        }

        // The strip's own bounds, always visible — the map's presence never
        // depends on there being any read data to shade it with.
        drawRect(ink.copy(alpha = 0.3f), size = size, style = Stroke(1.dp.toPx()))
        // Outline the aimed sector (the whole strip when Global).
        when (selected) {
            Region.GLOBAL -> drawRect(ink.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
            Region.AUSTRALIA_PACIFIC -> {
                outline(selected.fromLon, selected.toLon)
                outline(-180.0, Region.AMERICAS.fromLon)
            }
            else -> outline(selected.fromLon, selected.toLon)
        }
    }
}

/**
 * The heat strip in words: every sector with a read, named with its number,
 * densest first, and the current selection said out loud rather than left to
 * a highlight. Sectors with nothing read are omitted — naming eight zeroes
 * before the two that matter buries the answer.
 *
 * `@Composable` so it can reach `stringResource`; assembled from literals it
 * was English in every locale.
 */
@Composable
private fun describeHeatStrip(reads: Map<Region, Int>, selected: Region): String {
    val withReads = reads.entries
        .filter { it.key != Region.GLOBAL && it.value > 0 }
        .sortedByDescending { it.value }
    val head = stringResource(Res.string.heatstrip_title, selected.label)
    if (withReads.isEmpty()) return head + " " + stringResource(Res.string.heatstrip_empty)
    val total = withReads.sumOf { it.value }
    // `map` is inline and so is a composable scope; `joinToString`'s transform
    // is not, which is why the pieces are resolved before being joined.
    val named = withReads
        .map { stringResource(Res.string.loom_topic_count, it.key.label, it.value) }
        .joinToString(", ")
    val stories = stringResource(
        if (total == 1) Res.string.heatstrip_story_one else Res.string.heatstrip_story_many,
    )
    return head + " " + stringResource(Res.string.heatstrip_read, total, stories, named)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionChips(selected: Region, onSet: (Region) -> Unit, ink: Color, muted: Color) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
    ) {
        for (region in Region.entries) {
            val chosen = region == selected
            Text(
                region.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                color = if (chosen) ink else muted,
                modifier = Modifier
                    .clickable { onSet(region) }
                    .padding(vertical = Tokens.Spacing.xxs),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicChips(topicKeys: Set<String>, onToggle: (String) -> Unit, ink: Color, muted: Color) {
    val allTopics = topicKeys.isEmpty()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
    ) {
        for (topic in Topic.entries) {
            // With no explicit selection every topic is in play, so show them
            // all lit; once you pick any, only the picked ones read as chosen.
            val chosen = allTopics || topic.key in topicKeys
            Text(
                topic.placeholderLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (topic.key in topicKeys) FontWeight.Bold else FontWeight.Normal,
                color = if (chosen) ink else muted,
                modifier = Modifier
                    .clickable { onToggle(topic.key) }
                    .padding(vertical = Tokens.Spacing.xxs),
            )
        }
    }
}

/**
 * The funnel as one bar: the whole track is what flowed (faintest), the filter
 * sits over it (mid), the read sits over that (solid) — nested left-aligned, so
 * the bar simply *shrinks* through the two omissions.
 */
@Composable
private fun NestedFunnel(filterFraction: Float, readFraction: Float, ink: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(Tokens.Radius.sm))
            .background(ink.copy(alpha = 0.22f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(filterFraction.coerceIn(0f, 1f))
                .height(14.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(ink.copy(alpha = 0.5f)),
        )
        Box(
            Modifier
                .fillMaxWidth(readFraction.coerceIn(0f, 1f))
                .height(14.dp)
                .clip(RoundedCornerShape(Tokens.Radius.sm))
                .background(ink),
        )
    }
}

@Composable
private fun FunnelLegend(label: String, count: Int, swatch: Color, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(swatch))
        Column {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count", style = MaterialTheme.typography.titleMedium, color = ink)
        }
    }
}

/**
 * One topic as a dumbbell: a faint hollow dot at its share of the stream, a
 * solid dot at its share of your reading, a hairline connecting them. The gap
 * between the dots is the omission, read at a glance.
 */
@Composable
private fun DumbbellRow(row: ContrastRow, maxShare: Float, ink: Color, muted: Color) {
    val topicColor = row.topic.toComposeColor()
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(topicColor))
            Text(
                row.topic.placeholderLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = ink,
                modifier = Modifier.weight(1f).padding(start = Tokens.Spacing.xs),
            )
            Text(
                stringResource(
                    Res.string.contrast_share_pair,
                    (row.flowedShare * 100).roundToInt(),
                    (row.readShare * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
        val flowedX = (row.flowedShare / maxShare).coerceIn(0f, 1f)
        val readX = (row.readShare / maxShare).coerceIn(0f, 1f)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(16.dp),
        ) {
            val y = size.height / 2f
            val pad = 5.dp.toPx()
            val usable = size.width - pad * 2
            val fx = pad + flowedX * usable
            val rx = pad + readX * usable
            // The baseline track.
            drawLine(muted.copy(alpha = 0.25f), Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 1.dp.toPx())
            // The connector = the gap.
            drawLine(topicColor.copy(alpha = 0.5f), Offset(fx, y), Offset(rx, y), strokeWidth = 2.dp.toPx())
            // Flowed: a hollow ring (the potential).
            drawCircle(topicColor.copy(alpha = 0.55f), radius = 4.dp.toPx(), center = Offset(fx, y), style = Stroke(width = 1.5.dp.toPx()))
            // Read: a solid dot (the actual).
            drawCircle(topicColor, radius = 4.5.dp.toPx(), center = Offset(rx, y))
        }
    }
}

@Composable
private fun DotKey(filled: Boolean, label: String, ink: Color, muted: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
        Canvas(Modifier.size(10.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            if (filled) {
                drawCircle(ink, radius = 4.dp.toPx(), center = c)
            } else {
                drawCircle(ink, radius = 3.5.dp.toPx(), center = c, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
    }
}
