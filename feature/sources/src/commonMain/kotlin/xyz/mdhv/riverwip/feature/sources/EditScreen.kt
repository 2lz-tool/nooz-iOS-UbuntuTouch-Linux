package xyz.mdhv.riverwip.feature.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.AppSearchBar
import xyz.mdhv.riverwip.design.GlobeCanvas
import xyz.mdhv.riverwip.design.NoResultsState
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.topFadingEdge
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.feature.river.CrossSectionPanel
import xyz.mdhv.riverwip.model.SourceSearch
import xyz.mdhv.riverwip.model.GlobeModel
import xyz.mdhv.riverwip.model.HealthStatus
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.ServiceDef
import xyz.mdhv.riverwip.model.Topic
import xyz.mdhv.riverwip.model.toSourceOrNull

enum class EditTab { SOURCES, REGION_TOPICS, SETTINGS, ABOUT }

/**
 * Nooz EDIT (owner's mocks + flow map, extended 2026-07): four tabs. Sources:
 * one-click starters with check-circle toggles and add-by-URL with an honest
 * error state; Region & Topics: the globe, the topic chips, and the metrics
 * block (coverage, breadth, per-topic over/under, every number
 * tap-explained); Reader (labeled "Settings" until the owner's rename): the
 * full app settings, shown here directly rather than behind a separate gear
 * (owner: "it needn't be in the settings cog"); About: the studio/app info,
 * pulled out into its own tab rather than buried at the bottom of Reader's
 * long scroll (owner: "About needs to be a separate tab"). [settingsTab] and
 * [aboutTab] are caller-supplied slots (the same pattern as
 * [xyz.mdhv.riverwip.feature.reader.ReaderScreen]'s `settingsRoom`) so this
 * feature module never needs a dependency on wherever Settings actually
 * lives. DONE saves the filter draft and returns to the Stand.
 */
@Composable
fun EditScreen(
    vm: SourcesViewModel,
    onDone: () -> Unit,
    settingsTab: @Composable () -> Unit,
    aboutTab: @Composable () -> Unit,
    startTab: EditTab = EditTab.SOURCES,
) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    val savedFilter by vm.filter.collectAsStateWithLifecycle()

    // Filter draft: seeded from the saved filter, committed on DONE.
    var draftRegionKey by rememberSaveable(savedFilter.region.key) { mutableStateOf(savedFilter.region.key) }
    val topicsSaver = remember {
        listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })
    }
    var draftTopics by rememberSaveable(savedFilter.topicKeys, stateSaver = topicsSaver) {
        mutableStateOf(savedFilter.topicKeys)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The masthead is its own baseline-aligned unit (matching the Stand
            // and the loom): "Nooz" and stringResource(Res.string.edit_edit) share a baseline *inside* this
            // inner Row, and the inner Row is then centred whole in the header.
            // Putting alignByBaseline directly on the outer row's children — the
            // way this used to — made the pair share a baseline with nothing,
            // so the 48dp Settings/DONE controls dragged the masthead up and it
            // read as squished/misaligned (owner B4). EDIT's cap height ≈0.61×
            // Nooz's, so the two still sit on one baseline at 30/18sp.
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoozWordmark(fontSize = 30.sp, modifier = Modifier.alignByBaseline())
                Text(
                    stringResource(Res.string.edit_edit),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = Tokens.Spacing.sm),
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                vm.saveFilter(ReaderFilter(Region.fromKey(draftRegionKey), draftTopics))
                onDone()
            }) {
                Text(stringResource(Res.string.edit_done), style = MaterialTheme.typography.labelLarge)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Spacing.md),
        ) {
            TabLabel("Sources", tab == EditTab.SOURCES) { tab = EditTab.SOURCES }
            Spacer(Modifier.width(Tokens.Spacing.xl))
            TabLabel("Region & Topics", tab == EditTab.REGION_TOPICS) { tab = EditTab.REGION_TOPICS }
            Spacer(Modifier.width(Tokens.Spacing.xl))
            TabLabel("Reader", tab == EditTab.SETTINGS) { tab = EditTab.SETTINGS }
            Spacer(Modifier.width(Tokens.Spacing.xl))
            TabLabel("About", tab == EditTab.ABOUT) { tab = EditTab.ABOUT }
        }
        // More air above the rule than before (owner: "the line is sticking too
        // close to the headers") — enough that it reads as a column-header rule
        // opening the content below, not a second underline crowding the tabs.
        Spacer(Modifier.height(Tokens.Spacing.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Tokens.Spacing.xs))

        when (tab) {
            EditTab.SOURCES -> SourcesTab(vm)
            EditTab.REGION_TOPICS -> RegionTopicsTab(
                vm = vm,
                regionKey = draftRegionKey,
                topics = draftTopics,
                onRegion = { draftRegionKey = it.key },
                onTopics = { draftTopics = it },
            )
            EditTab.SETTINGS -> settingsTab()
            EditTab.ABOUT -> aboutTab()
        }
    }
}

@Composable
private fun TabLabel(label: String, active: Boolean, onClick: () -> Unit) {
    // The underline is *drawn* under the text (drawBehind), sized to the text's
    // own measured width, rather than being a sibling Box whose fillMaxWidth
    // has to be corralled by intrinsic-width tricks. Earlier attempts pinned
    // the Column to the text width via IntrinsicSize.Min (sized to the longest
    // *word*, so "Region & Topics" wrapped to three lines) then IntrinsicSize.Max
    // (still wrapped on-device). Drawing the rule inside the Text's own bounds
    // sidesteps intrinsic measurement entirely — no horizontal padding is ever
    // added here, so size.width always matches the single-line text's own
    // width exactly (owner's #6, recurring).
    val underline = MaterialTheme.colorScheme.onBackground
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            // drawBehind must wrap padding, not the other way around (owner:
            // "the underline of the selected is still sticking to the
            // selected text") — a modifier only sees the size of what's
            // *inside* it, so with padding applied first, drawBehind's own
            // `size` was just the bare text, never the padded box, and every
            // extra bit of bottom padding only pushed the divider further
            // away without ever moving the line off the text itself.
            .drawBehind {
                if (active) {
                    val stroke = 3.dp.toPx()
                    val y = size.height - stroke
                    drawLine(
                        color = underline,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                }
            }
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .padding(top = Tokens.Spacing.xs, bottom = Tokens.Spacing.lg),
    )
}

// ---------------------------------------------------------------- Sources tab

@Composable
private fun SourcesTab(vm: SourcesViewModel) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val startersByRegion by vm.startersByRegion.collectAsStateWithLifecycle()
    val builders by vm.builders.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var regionFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val addedIds = remember(sources) { sources.map { it.id }.toSet() }
    val regions = remember(startersByRegion) { startersByRegion.keys.sorted() }
    val flatStarters = remember(startersByRegion) {
        startersByRegion.entries.sortedBy { it.key }.flatMap { (region, defs) -> defs.map { region to it } }
    }
    // The region chips narrow first, then the query matches and ranks what's
    // left (D39). This used to be one substring test against `title` alone,
    // which stopped being adequate somewhere around the catalogue's second
    // hundred entries: word order mattered, a domain or a language name found
    // nothing, and every hit was equal so an incidental URL match could outrank
    // an exact masthead.
    val regionScoped = flatStarters.filter { (region, _) -> regionFilter == null || region == regionFilter }
    val filteredStarters = SourceSearch.rank(regionScoped, query) { it.second }
    val filteredBuilders = if (regionFilter != null) emptyList() else SourceSearch.rank(builders, query) { it }

    Column(Modifier.fillMaxSize()) {
        // Region filter chips — narrow the (now large) list by sector.
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
        ) {
            FilterChip(selected = regionFilter == null, onClick = { regionFilter = null }, label = { Text(stringResource(Res.string.edit_all)) })
            for (r in regions) {
                FilterChip(
                    selected = regionFilter == r,
                    onClick = { regionFilter = if (regionFilter == r) null else r },
                    label = { Text(r.replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        val listScroll = rememberScrollState()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .topFadingEdge(listScroll.canScrollBackward)
                .verticalScroll(listScroll)
                .padding(horizontal = Tokens.Spacing.md),
        ) {
            if (filteredStarters.isEmpty() && filteredBuilders.isEmpty()) {
                // A literal no-results case (a search/region filter that came up
                // empty) — the owner's illustration + quote treatment, not the
                // "why is this empty" EmptyState. stringResource(Res.string.edit_add_by_url) is still right
                // below, so the way out stays visible either way.
                NoResultsState(fill = false, modifier = Modifier.fillMaxWidth())
            }
            for ((region, def) in filteredStarters) {
                val id = remember(def) { def.toSourceOrNull(addedAt = 0L)?.id }
                StarterRow(
                    def = def,
                    region = if (regionFilter == null) region else null,
                    added = id != null && id in addedIds,
                    unhealthy = id != null && (health[id]?.status == HealthStatus.FAILING || health[id]?.status == HealthStatus.RATE_LIMITED),
                    onToggle = { vm.toggleStarter(def) },
                )
            }

            if (filteredBuilders.isNotEmpty()) {
                SectionHeading(
                    stringResource(Res.string.edit_news_apis),
                    modifier = Modifier.padding(top = Tokens.Spacing.lg, bottom = Tokens.Spacing.xs),
                )
                for (b in filteredBuilders) {
                    BuilderRow(def = b, onAdd = { vm.addByUrl(it) })
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = Tokens.Spacing.md),
            )
            AddByUrlSection(vm)
            Row(
                Modifier.padding(vertical = Tokens.Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
            ) {
                OpmlButtons(vm)
            }
            CatalogueCard(vm)
            Spacer(Modifier.height(Tokens.Spacing.xl))
        }

        // The search bar, docked to the bottom (owner's spec) — the shared bar
        // (core/design), now also reused by the Stand's article list.
        AppSearchBar(query = query, onQueryChange = { query = it }, placeholder = stringResource(Res.string.edit_search_sources))
    }
}

@Composable
private fun BuilderRow(def: ServiceDef, onAdd: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val example = def.example
    val oneTap = example != null && !example.contains("{")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(def.title, style = MaterialTheme.typography.titleMedium)
            val line = def.notes ?: def.homepage
            if (line != null) {
                Text(
                    if (def.requiresKey) "Needs a free API key. $line" else line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        // Minimal (owner: "just a big plus icon rather than a button and text"):
        // a plus adds a one-tap builder; a key opens the free-key signup.
        when {
            def.requiresKey && def.keySignupUrl != null -> IconButton(onClick = {
                def.keySignupUrl?.let { uriHandler.openUri(it) }
            }) {
                Icon(Icons.Filled.Key, contentDescription = stringResource(Res.string.edit_get_free_key, def.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            oneTap -> IconButton(onClick = { onAdd(example!!) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.edit_add_named, def.title))
            }
            else -> {}
        }
    }
}

/**
 * Bleeds [horizontal] past an ancestor's own inset on each side (owner: "the
 * grey on click state is not edge to edge yet") — [SourcesTab]'s scrolling
 * column insets every row by [Tokens.Spacing.md] so text lines up with the
 * region chips and search bar above/below it, but that same inset was
 * shrinking a row's own `clickable` ripple/press background short of the
 * screen's true edges. Applied before `clickable` so its indication paints
 * across the full bled width; the row's own content re-adds the same inset
 * afterward, so nothing but the ripple actually moves.
 */
private fun Modifier.bleedHorizontal(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val horizontalPx = horizontal.roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + horizontalPx * 2))
    // Report the ORIGINAL (un-widened) width back to the parent -- this node
    // must claim only the space it was actually given, so the parent Column
    // keeps placing it (and everything after it) exactly where it would
    // without this modifier. Reporting the wider measured width instead (a
    // bug this fixed: the parent then thought this row started `horizontal`
    // further right than it actually draws, so the left inset the ancestor
    // column's own padding added was silently cancelled out, leaving the row
    // flush against the true screen edge instead of lined up with the chips
    // and header above it) would misalign every row against everything else
    // on the same screen that isn't bleeding.
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative(-horizontalPx, 0)
    }
}

@Composable
private fun StarterRow(def: ServiceDef, region: String?, added: Boolean, unhealthy: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bleedHorizontal(Tokens.Spacing.md)
            .clickable(onClickLabel = if (added) "Remove ${def.title}" else stringResource(Res.string.edit_add_named, def.title), onClick = onToggle)
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(def.title, style = MaterialTheme.typography.titleMedium)
            val line = listOfNotNull(region?.replaceFirstChar { it.uppercase() }, def.url ?: def.notes)
                .joinToString(" · ")
                .ifBlank { null }
            if (line != null) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (unhealthy) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = stringResource(Res.string.edit_source_failing),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = Tokens.Spacing.xs).size(18.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (added) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (added) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AddByUrlSection(vm: SourcesViewModel) {
    // Resolved outside `semantics { }`, which is not a composable scope.
    val feedUrlLabel = stringResource(Res.string.edit_feed_or_site_url)
    var url by rememberSaveable { mutableStateOf("") }
    val state = vm.addState

    SectionHeading(
        stringResource(Res.string.edit_add_by_url),
        modifier = Modifier.padding(top = Tokens.Spacing.lg, bottom = Tokens.Spacing.xs),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = url,
            onValueChange = { url = it; vm.resetAddState() },
            textStyle = TextStyle.Default.merge(MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground)),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = feedUrlLabel },
        )
        if (state is AddUiState.Error) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = stringResource(Res.string.edit_could_not_add_url),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        } else {
            IconButton(onClick = { if (url.isNotBlank()) vm.addByUrl(url) }, enabled = state !is AddUiState.Loading) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.edit_add_this_url))
            }
        }
    }
    HorizontalDivider(
        color = if (state is AddUiState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
    )
    when (state) {
        is AddUiState.Error -> Text(
            stringResource(Res.string.edit_could_not_add, state.message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Assertive }
                .padding(top = Tokens.Spacing.xxs),
        )
        is AddUiState.Added -> Text(
            stringResource(Res.string.edit_added_named, state.title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(top = Tokens.Spacing.xxs),
        )
        is AddUiState.Choices -> Column {
            Text(stringResource(Res.string.edit_pick_a_feed), style = MaterialTheme.typography.bodySmall)
            state.candidates.forEach { feed ->
                TextButton(onClick = { vm.addCandidate(feed) }) {
                    Text((feed.title ?: feed.url) + "  ·  ${feed.type.name.lowercase()}")
                }
            }
        }
        else -> {}
    }
    Text(
        stringResource(Res.string.edit_paste_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Tokens.Spacing.xs),
    )
}

// --------------------------------------------------------- Region & Topics tab

@Composable
private fun RegionTopicsTab(
    vm: SourcesViewModel,
    regionKey: String,
    topics: Set<String>,
    onRegion: (Region) -> Unit,
    onTopics: (Set<String>) -> Unit,
) {
    val mixByRegion by vm.mixByRegion.collectAsStateWithLifecycle()
    val aggregates by vm.aggregates.collectAsStateWithLifecycle()

    val savedRegion = Region.fromKey(regionKey)
    var yaw by rememberSaveable(regionKey) {
        mutableDoubleStateOf(
            if (savedRegion == Region.GLOBAL) 20.0 else -(savedRegion.fromLon + savedRegion.toLon) / 2,
        )
    }
    var pitch by rememberSaveable { mutableDoubleStateOf(-10.0) }
    var bandHalf by rememberSaveable(regionKey) {
        mutableDoubleStateOf(if (savedRegion == Region.GLOBAL) 180.0 else 16.0)
    }

    // The single region the drag/pinch gesture actually commits (unchanged
    // contract with the caller): whichever sector the exact band centre sits
    // in, or Global past the threshold.
    val aimedRegion = if (bandHalf >= GlobeModel.GLOBAL_BAND_THRESHOLD) {
        Region.GLOBAL
    } else {
        Region.forLongitude(GlobeModel.centerLongitude(yaw))
    }
    // Everything the band actually *touches* — for display only (owner's #8:
    // widening the band used to visibly grow the guide lines while the aimed
    // region and ring stayed frozen on one sector until the band hit the
    // global threshold, so the picker read as a binary one-region/Global
    // switch with nothing in between). A widened band now names every sector
    // it spans and blends their real topic counts into one ring, so the
    // in-between states the gesture already drew are also the ones it shows.
    val bandRegions = Region.forBand(GlobeModel.centerLongitude(yaw), bandHalf)
    val bandRingMix = if (bandRegions.size <= 1) {
        mixByRegion[bandRegions.first()].orEmpty()
    } else {
        buildMap {
            for (r in bandRegions) mixByRegion[r]?.forEach { (topic, count) -> merge(topic, count, Int::plus) }
        }
    }
    val bandLabel = when {
        bandRegions.size <= 1 -> aimedRegion.label
        bandRegions.size <= 3 -> bandRegions.joinToString(" + ") { it.label }
        else -> "${bandRegions.first().label} +${bandRegions.size - 1} more"
    }

    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .topFadingEdge(scroll.canScrollBackward)
            .verticalScroll(scroll)
            .padding(horizontal = Tokens.Spacing.md),
    ) {
        Text(
            stringResource(Res.string.edit_globe_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.sm),
        )
        GlobeCanvas(
            yaw = yaw,
            pitch = pitch,
            bandHalf = bandHalf,
            ringMix = bandRingMix,
            onSpin = { dYaw, dPitch ->
                yaw += dYaw
                pitch = (pitch + dPitch).coerceIn(-70.0, 70.0)
                onRegion(
                    if (bandHalf >= GlobeModel.GLOBAL_BAND_THRESHOLD) Region.GLOBAL
                    else Region.forLongitude(GlobeModel.centerLongitude(yaw)),
                )
            },
            onZoomBand = { factor ->
                bandHalf = (bandHalf * factor).coerceIn(8.0, 180.0)
                onRegion(
                    if (bandHalf >= GlobeModel.GLOBAL_BAND_THRESHOLD) Region.GLOBAL
                    else Region.forLongitude(GlobeModel.centerLongitude(yaw)),
                )
            },
            modifier = Modifier.padding(horizontal = Tokens.Spacing.xl),
        )
        Text(
            bandLabel,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Tokens.Spacing.sm),
        )
        Text(
            if (aimedRegion == Region.GLOBAL) "full width" else "band ±${bandHalf.toInt()}°",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.sm), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = {
                yaw = 20.0
                pitch = -10.0
                bandHalf = 180.0
                onRegion(Region.GLOBAL)
            }) { Text(stringResource(Res.string.edit_reset_to_global)) }
        }

        // Topics: the filter's other half. Chips, not gestures — no hidden doors.
        SectionHeading(
            stringResource(Res.string.list_topics),
            modifier = Modifier.padding(top = Tokens.Spacing.sm, bottom = Tokens.Spacing.xxs),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
        ) {
            FilterChip(selected = topics.isEmpty(), onClick = { onTopics(emptySet()) }, label = { Text(stringResource(Res.string.edit_all)) })
            for (topic in Topic.entries) {
                FilterChip(
                    selected = topic.key in topics,
                    onClick = {
                        onTopics(if (topic.key in topics) topics - topic.key else topics + topic.key)
                    },
                    label = { Text(topic.placeholderLabel) },
                )
            }
        }

        if (aggregates.isNotEmpty()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = Tokens.Spacing.md),
            )
            CrossSectionPanel(aggregates, aggregates.lastIndex)
        }
        Spacer(Modifier.height(Tokens.Spacing.xl))
    }
}
