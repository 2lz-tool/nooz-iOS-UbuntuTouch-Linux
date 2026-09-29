package xyz.mdhv.riverwip.feature.lens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import xyz.mdhv.riverwip.design.Copy
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.model.AffectSpanDetector

/**
 * Tap a span → this sheet (brief §P5): original text, the evidence, a proposed
 * neutral rewrite (with provenance), accept/dismiss. Detection is framed as
 * opinion, never verdict (brief §7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefuseBottomSheet(
    itemId: String,
    span: AffectSpanDetector.Span,
    originalText: String,
    fullSentence: String,
    vm: LensViewModel,
    onDismissRequest: () -> Unit,
) {
    val state = vm.stateFor(itemId, span)

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            // The rewrite state below changes without any user action of its own
            // (Loading -> Accepted/Rejected) — a live region announces that
            // transition to TalkBack instead of leaving it silent.
            modifier = Modifier.padding(Tokens.Spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        ) {
            // Hoisted: a semantics block is not a composable scope.
            val loadingLabel = stringResource(Res.string.defuse_loading)
            Text(stringResource(Res.string.defuse_original), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(originalText, style = MaterialTheme.typography.bodyLarge)
            Text(span.evidence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Copy.DETECTION_IS_OPINION,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (state) {
                is AffectSpanUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = Tokens.Spacing.sm)
                            .semantics { contentDescription = loadingLabel },
                    )
                }
                is AffectSpanUiState.Accepted -> {
                    Text(
                        stringResource(Res.string.defuse_proposed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Tokens.Spacing.xs),
                    )
                    Text(state.rewrittenText, style = MaterialTheme.typography.bodyLarge)
                    val provenanceLabel = if (state.provenance == Provenance.NATIVE) {
                        stringResource(Res.string.provenance_on_device)
                    } else {
                        stringResource(Res.string.provenance_cloud_urbana)
                    }
                    val provenanceColor = if (state.provenance == Provenance.NATIVE) Tokens.Color.provenanceNative else Tokens.Color.provenanceCloud
                    Text(provenanceLabel, style = MaterialTheme.typography.labelSmall, color = provenanceColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                        TextButton(onClick = { vm.revert(itemId, span); onDismissRequest() }) { Text(stringResource(Res.string.defuse_revert)) }
                    }
                }
                is AffectSpanUiState.Rejected -> {
                    // Not an error the reader caused — a capability that isn't
                    // set up yet. Muted, not alarming red (owner).
                    Text(
                        stringResource(Res.string.defuse_needs_setup),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Tokens.Spacing.xs),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                        TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.defuse_close)) }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                        Button(onClick = { vm.requestDefuse(itemId, span, fullSentence) }) { Text(stringResource(Res.string.defuse_suggest)) }
                        TextButton(onClick = { vm.dismiss(itemId, span); onDismissRequest() }) { Text(stringResource(Res.string.settings_dismiss)) }
                    }
                }
            }
        }
    }
}
