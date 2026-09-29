package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.toComposeColor
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.model.RiverAnalysis
import xyz.mdhv.riverwip.model.Topic
import xyz.mdhv.riverwip.model.WeeklyAggregate
import xyz.mdhv.riverwip.model.readByTopic
import xyz.mdhv.riverwip.model.streamByTopic
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val WEEK_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * The cross-section panel (brief §P4): every metric here is tap-explained with
 * its formula, and the supply-vs-drift decomposition — the analytical
 * differentiator — is rendered in plain language, its components summing
 * exactly to the observed change (verified in `RiverAnalysisTest`).
 */
@Composable
fun CrossSectionPanel(aggregates: List<WeeklyAggregate>, selectedIndex: Int, modifier: Modifier = Modifier) {
    val agg = aggregates[selectedIndex]
    val stream = agg.streamByTopic()
    val read = agg.readByTopic()
    val coverage = RiverAnalysis.coverage(stream, read)
    val breadthStream = RiverAnalysis.breadth(stream)
    val breadthRead = RiverAnalysis.breadth(read)
    val overUnder = RiverAnalysis.overUnderRatio(stream, read).entries.sortedByDescending { it.value }

    val decomposition = if (selectedIndex > 0) {
        val prev = aggregates[selectedIndex - 1]
        RiverAnalysis.decompose(prev.streamByTopic(), prev.readByTopic(), stream, read)
            .filter { abs(it.observedDelta) > 1e-6 }
            .sortedByDescending { abs(it.observedDelta) }
    } else {
        emptyList()
    }

    Column(modifier = modifier.fillMaxWidth().padding(Tokens.Spacing.md), verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
        Text(
            WEEK_LABEL_FORMAT.format(Instant.ofEpochMilli(agg.weekStart).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.titleMedium,
        )

        MetricRow(
            label = stringResource(Res.string.cross_coverage),
            value = "${(coverage * 100).roundToInt()}%",
            formula = "items you opened ÷ items that flowed, this period",
        )
        MetricRow(
            label = stringResource(Res.string.cross_breadth_stream),
            value = "%.1f effective topics".format(breadthStream),
            formula = "exp(Shannon entropy) of the stream's topic mix: how spread across topics the supply was",
        )
        MetricRow(
            label = stringResource(Res.string.cross_breadth_you),
            value = "%.1f effective topics".format(breadthRead),
            formula = "exp(Shannon entropy) of what you read: how spread across topics your reading was",
        )

        Text(stringResource(Res.string.cross_over_under), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Tokens.Spacing.xs))
        for ((topic, ratio) in overUnder) {
            OverUnderRow(topic, ratio)
        }

        Text(
            stringResource(Res.string.cross_what_changed),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = Tokens.Spacing.xs),
        )
        if (selectedIndex == 0) {
            Text(
                stringResource(Res.string.cross_no_earlier),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (decomposition.isEmpty()) {
            Text(
                stringResource(Res.string.cross_steady),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            for (d in decomposition.take(5)) {
                DecompositionRow(d)
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, formula: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Hide formula" else "Show formula") { expanded = !expanded }
            .semantics(mergeDescendants = true) { stateDescription = if (expanded) "expanded" else "collapsed" }
            .padding(vertical = Tokens.Spacing.xxs),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        if (expanded) {
            // Total inspectability (brief §3): tap any number to see the exact formula.
            Text(formula, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OverUnderRow(topic: Topic, ratio: Double) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Hide formula" else "Show formula") { expanded = !expanded }
            .semantics(mergeDescendants = true) { stateDescription = if (expanded) "expanded" else "collapsed" }
            .padding(vertical = Tokens.Spacing.xxs),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(topic.toComposeColor()),
                )
                Spacer(modifier = Modifier.padding(start = Tokens.Spacing.xxs))
                // Colour is never the only channel (brief §2): label always present.
                Text(topic.placeholderLabel, style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(Res.string.cross_section_ratio, ratio), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        if (expanded) {
            Text(
                stringResource(Res.string.cross_section_explainer, topic.placeholderLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DecompositionRow(d: RiverAnalysis.TopicDecomposition) {
    val direction = if (d.observedDelta > 0) "rose" else "fell"
    val supplyPercent = d.supplyPercent
    val selectionPercent = d.selectionPercent
    val sentence = if (supplyPercent != null && selectionPercent != null) {
        "${d.topic.placeholderLabel} $direction in your reading: ~${supplyPercent.roundToInt()}% because the stream shifted, ~${selectionPercent.roundToInt()}% because you chose differently."
    } else {
        "${d.topic.placeholderLabel} $direction in your reading."
    }
    Text(
        sentence,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = Tokens.Spacing.xxs),
    )
}
