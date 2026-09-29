package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.EmptyState
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.StoryClustering

/**
 * The framing lens (owner's contrast idea, phase 3): stories that more than one
 * of your sources covered, with each outlet's headline set side by side — so
 * the difference in how they framed the same event (the "invades" vs "cross
 * into" contrast) is there to read. Clustering is on-device and needs no model;
 * the *automatic* marking of which words are the loaded ones is the lens's job
 * and waits on a real inference provider — until then, the comparison is yours
 * to make, which is the honest version anyway.
 */
@Composable
fun FramingsPanel(
    items: List<Item>,
    windowStart: Long,
    windowEndExclusive: Long,
    sourceTitles: Map<String, String>,
    onOpenItem: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clusters = remember(items, windowStart, windowEndExclusive) {
        val inWindow = items.filter { it.publishedAt in windowStart until windowEndExclusive }
        val byId = inWindow.associateBy { it.id }
        StoryClustering
            .cluster(inWindow.map { StoryClustering.Doc(it.id, it.title, it.sourceId) })
            .map { cluster -> cluster to cluster.members.mapNotNull { byId[it.id] } }
    }

    if (clusters.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.framings_empty),
            body = "Framings appear when two of your sources tell the same story: set side by side, the wording each chose is the contrast.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tokens.Spacing.lg, vertical = Tokens.Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xl),
    ) {
        items(clusters, key = { it.first.members.first().id }) { (cluster, members) ->
            FramingCard(
                keywords = cluster.keywords,
                sourceCount = cluster.sourceCount,
                members = members,
                sourceTitles = sourceTitles,
                onOpenItem = onOpenItem,
            )
        }
    }
}

@Composable
private fun FramingCard(
    keywords: List<String>,
    sourceCount: Int,
    members: List<Item>,
    sourceTitles: Map<String, String>,
    onOpenItem: (Item) -> Unit,
) {
    // No filled card (owner: minimal): a hairline caps the group, the rough
    // subject sits above, and each outlet's framing follows — nothing but ink
    // and space doing the work.
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            (keywords.joinToString(" · ").ifBlank { "same story" } + "  ·  $sourceCount sources").uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        for (item in members) {
            FramingLine(
                source = sourceTitles[item.sourceId] ?: "",
                title = item.title,
                onClick = { onOpenItem(item) },
            )
        }
    }
}

@Composable
private fun FramingLine(source: String, title: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs),
    ) {
        if (source.isNotBlank()) {
            Text(
                source.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
