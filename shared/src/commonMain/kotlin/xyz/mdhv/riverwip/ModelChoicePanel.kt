package xyz.mdhv.riverwip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.data.repo.CatalogueModel
import xyz.mdhv.riverwip.data.repo.ModelDownloadState
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.inference.byok.ByokConfig
import xyz.mdhv.riverwip.inference.local.StorageBudget

/** Which of the lens's three intelligence paths the reader is looking at. */
enum class ModelPath { ON_DEVICE, DOWNLOAD, BYOK }

/**
 * The download stanza's data + actions, bundled so [ModelChoicePanel] stays a
 * single parameter list regardless of which screen hosts it.
 */
data class ModelDownloadUi(
    val models: List<CatalogueModel>,
    val downloadStates: Map<String, ModelDownloadState>,
    val isDownloaded: (CatalogueModel) -> Boolean,
    val onDownload: (CatalogueModel) -> Unit,
    val onDelete: (CatalogueModel) -> Unit,
    val onRefresh: () -> Unit,
    val refreshing: Boolean,
    val error: String?,
)

/**
 * The one honest "how should the lens think" chooser (owner's #18; a
 * cross-repo consumer of Nooz's catalogue flagged the same gap: the two
 * models this app once hardcoded had no verified download URL, so a one-tap
 * "download" button for THEM would be fake or broken). This app now reads the
 * constellation's own real, live-probed `ai-catalogue/models.json` instead —
 * three real paths, shown the same way wherever this question comes up:
 * onboarding's Advanced step and Settings' Reader intelligence share this
 * exact panel, so a reader who skips it in onboarding finds the identical
 * doors later rather than a diminished version.
 *
 * - **On-device**: the default, nothing to configure — a real llama.cpp
 *   runtime (`core/inference/src/main/cpp`) runs whichever model was
 *   downloaded below; until one is, the lens honestly reports "unavailable"
 *   instead of guessing.
 * - **Download a model**: the catalogue's real, policy-safe entries for
 *   whichever `kind` the caller asked for (LLM weights for the lens, Kokoro's
 *   ONNX files for Nooz Cast) — sized, one tap, a real progress bar, a real
 *   file on disk afterward.
 * - **Bring your own key**: runs end-to-end today too, same as on-device.
 */
@Composable
fun ModelChoicePanel(
    path: ModelPath,
    onPathChange: (ModelPath) -> Unit,
    byokConfig: ByokConfig,
    onSaveByok: (baseUrl: String, apiKey: String, model: String) -> Unit,
    onClearByok: () -> Unit,
    download: ModelDownloadUi,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
            PathChip("On-device", path == ModelPath.ON_DEVICE) { onPathChange(ModelPath.ON_DEVICE) }
            PathChip("Download a model", path == ModelPath.DOWNLOAD) { onPathChange(ModelPath.DOWNLOAD) }
            PathChip("Bring your own key", path == ModelPath.BYOK) { onPathChange(ModelPath.BYOK) }
        }
        when (path) {
            ModelPath.ON_DEVICE -> OnDeviceStanza()
            ModelPath.DOWNLOAD -> ModelDownloadList(download)
            ModelPath.BYOK -> ByokStanza(byokConfig, onSaveByok, onClearByok)
        }
    }
}

@Composable
private fun PathChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun OnDeviceStanza() {
    Text(
        stringResource(Res.string.model_on_device_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The download list on its own — [ModelChoicePanel]'s "Download a model" path
 * uses this, and so does Nooz Cast's own model section in Settings, since Cast
 * has no on-device/BYOK trichotomy to choose from (an on-device-only model,
 * downloaded or not), just this list scoped to its own `kind`.
 */
@Composable
fun ModelDownloadList(ui: ModelDownloadUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            stringResource(Res.string.model_download_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (model in ui.models) {
            ModelRow(model, ui)
        }
        if (ui.models.isEmpty()) {
            Text(
                stringResource(Res.string.model_none_available),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Tokens.Spacing.xs),
            )
        }
        TextButton(
            onClick = ui.onRefresh,
            enabled = !ui.refreshing,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.padding(top = Tokens.Spacing.xs),
        ) { Text(if (ui.refreshing) "Refreshing…" else "Refresh list") }
        ui.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ModelRow(model: CatalogueModel, ui: ModelDownloadUi) {
    val state = ui.downloadStates[model.id]
    val downloaded = state is ModelDownloadState.Ready || (state == null && ui.isDownloaded(model))
    Column(Modifier.fillMaxWidth().padding(top = Tokens.Spacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    listOfNotNull(StorageBudget.humanReadable(model.sizeBytes), model.hfRepo).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                model.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            when {
                state is ModelDownloadState.Downloading -> Text(
                    stringResource(Res.string.model_progress_percent, (state.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                downloaded -> TextButton(onClick = { ui.onDelete(model) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(Res.string.model_delete))
                }
                else -> TextButton(onClick = { ui.onDownload(model) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(Res.string.model_download))
                }
            }
        }
        if (state is ModelDownloadState.Downloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().padding(top = Tokens.Spacing.xxs),
            )
        }
        if (state is ModelDownloadState.Failed) {
            Text(state.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ByokStanza(
    config: ByokConfig,
    onSave: (String, String, String) -> Unit,
    onClear: () -> Unit,
) {
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var model by remember(config) { mutableStateOf(config.model) }

    Text(
        stringResource(Res.string.model_byok_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ModelChoiceField("Base URL", baseUrl, "https://api.openai.com/v1") { baseUrl = it }
    ModelChoiceField("API key", apiKey, "sk-…", masked = true) { apiKey = it }
    ModelChoiceField("Model", model, "gpt-4o-mini") { model = it }
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
        TextButton(
            onClick = { onSave(baseUrl, apiKey, model) },
            enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank(),
            contentPadding = PaddingValues(0.dp),
        ) { Text(stringResource(Res.string.model_save_key)) }
        if (config.isComplete) {
            TextButton(onClick = onClear, contentPadding = PaddingValues(0.dp)) { Text(stringResource(Res.string.model_remove_key)) }
        }
    }
}

@Composable
private fun ModelChoiceField(
    label: String,
    value: String,
    placeholder: String,
    masked: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = Tokens.Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xxs),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                inner()
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
