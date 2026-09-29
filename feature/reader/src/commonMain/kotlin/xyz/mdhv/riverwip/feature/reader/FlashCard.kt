package xyz.mdhv.riverwip.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.design.DisplayFontFamily
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.rememberAudioFilePlayer
import xyz.mdhv.riverwip.design.rememberTextSpeaker
import xyz.mdhv.riverwip.inference.Provenance
import java.util.Locale

/**
 * Nooz Flash (owner's #6, then owner's follow-up "it's unfindable"): today's
 * flowed headlines, compressed to 10 words or fewer. A bolt mark and a
 * bordered card give it a distinct silhouette on the Stand — plain muted
 * body text read as ordinary chrome, easy to scroll straight past. Never
 * fetched automatically — the reader taps for it, same "explicit consent"
 * contract every other generation in this app follows. "Go deeper" doesn't
 * call the model a second time; it just reveals the real headline list the
 * flash line was compressed from, so the honest source is always one tap
 * away, never a second, ungrounded generation. "Play" reads the flash line
 * aloud via the device's own on-device text-to-speech engine — no network,
 * matching the feature's on-device-first stance.
 *
 * Owner's report: the "not configured" state read as broken, a dead-end card
 * with no way forward. Its hint is now the actual door, not just a pointer to
 * one — tapping it opens Settings' Reader intelligence section directly
 * ([onOpenSetup]) rather than leaving the reader to find the menu themselves.
 */
@Composable
fun FlashCard(vm: ReaderViewModel, onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    CardShell(modifier) { FlashCardBody(vm, onOpenSetup) }
}

/** Shared bordered/rounded shell for the Stand's Flash/Cast slot (owner: "same visual slot Flash occupies today") — one silhouette whether it holds one piece or both. */
@Composable
private fun CardShell(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .border(BorderStroke(Tokens.Border.thin, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(Tokens.Radius.md))
            .padding(Tokens.Spacing.sm),
        content = content,
    )
}

@Composable
private fun FlashCardBody(vm: ReaderViewModel, onOpenSetup: () -> Unit) {
    val state by vm.flashState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    // A crossed-out bolt reads at a glance, without a tap first (owner's
    // ask): "not configured" is knowable upfront (see ReaderViewModel's
    // own preflight check), so the icon should say so immediately rather
    // than waiting for the reader to tap and be told in text alone.
    // Crossed-out bolt for anything the reader can't act on right now: an
    // unconfigured provider, or Flash itself being "coming soon" in this build.
    val notConfigured = state is FlashUiState.ComingSoon ||
        (state as? FlashUiState.Unavailable)?.needsSetup == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
        Icon(
            if (notConfigured) Icons.Filled.FlashOff else Icons.Filled.FlashOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp),
        )
        SectionHeading("Nooz Flash", color = MaterialTheme.colorScheme.onBackground)
    }
    when (val s = state) {
        is FlashUiState.ComingSoon -> Text(
            stringResource(Res.string.flash_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Tokens.Spacing.xxs),
        )
        is FlashUiState.Idle -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(Res.string.flash_action_compress)) { vm.requestFlash() }
                .padding(top = Tokens.Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.flash_tap_to_compress),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        is FlashUiState.Loading -> Row(
            modifier = Modifier.padding(top = Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                stringResource(Res.string.flash_compressing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is FlashUiState.Ready -> {
            Text(
                s.flash,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DisplayFontFamily),
                modifier = Modifier.padding(top = Tokens.Spacing.xxs),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm)) {
                val provenanceLabel = if (s.provenance == Provenance.NATIVE) "on-device" else "cloud (your key)"
                val provenanceColor = if (s.provenance == Provenance.NATIVE) Tokens.Color.provenanceNative else Tokens.Color.provenanceCloud
                Text(provenanceLabel, style = MaterialTheme.typography.labelSmall, color = provenanceColor)
                Text(
                    if (expanded) "Hide the headlines" else "Go deeper",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
                Spacer(Modifier.weight(1f))
                PlayTextButton(text = s.flash, playLabel = "Read the flash aloud")
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = Tokens.Spacing.xs)) {
                    for (headline in s.headlines) {
                        Text(
                            headline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Tokens.Spacing.xxs),
                        )
                    }
                }
            }
        }
        is FlashUiState.Unavailable -> Column(Modifier.padding(top = Tokens.Spacing.xxs)) {
            Text(
                s.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.needsSetup) {
                Text(
                    stringResource(Res.string.flash_setup_link),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clickable(onClickLabel = stringResource(Res.string.flash_action_setup)) { onOpenSetup() }
                        .padding(top = Tokens.Spacing.xxs),
                )
            }
        }
    }
}

/**
 * Nooz Cast's own card (owner: a natural on-device narrator, own gate, own
 * icon). Same silhouette and state-machine shape as [FlashCardBody] on
 * purpose — the reader learns one card idiom and both features read it.
 */
@Composable
private fun CastCardBody(vm: ReaderViewModel, onOpenSetup: () -> Unit) {
    val state by vm.castState.collectAsStateWithLifecycle()

    val notConfigured = (state as? CastUiState.Unavailable)?.needsSetup == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
        Icon(
            if (notConfigured) Icons.Filled.VoiceOverOff else Icons.Filled.RecordVoiceOver,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(18.dp),
        )
        SectionHeading("Nooz Cast", color = MaterialTheme.colorScheme.onBackground)
    }
    when (val s = state) {
        is CastUiState.Idle -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(Res.string.cast_action_narrate)) { vm.requestCast() }
                .padding(top = Tokens.Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.cast_tap_to_hear),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        is CastUiState.Loading -> Row(
            modifier = Modifier.padding(top = Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                stringResource(Res.string.cast_narrating),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is CastUiState.Ready -> Row(
            modifier = Modifier.padding(top = Tokens.Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
        ) {
            // Always on-device (owner: "a private anchor voice should never
            // leave the device") — no cloud branch to show, unlike Flash's.
            Text(stringResource(Res.string.provenance_on_device), style = MaterialTheme.typography.labelSmall, color = Tokens.Color.provenanceNative)
            Spacer(Modifier.weight(1f))
            PlayAudioFileButton(audioPath = s.audioPath)
        }
        is CastUiState.Unavailable -> Column(Modifier.padding(top = Tokens.Spacing.xxs)) {
            Text(
                s.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.needsSetup) {
                Text(
                    stringResource(Res.string.cast_setup_link),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clickable(onClickLabel = stringResource(Res.string.cast_action_setup)) { onOpenSetup() }
                        .padding(top = Tokens.Spacing.xxs),
                )
            }
        }
    }
}

/** Nooz Cast alone, same standalone look Flash gets from [FlashCard] — used when Cast is on but Flash isn't. */
@Composable
fun CastCard(vm: ReaderViewModel, onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    CardShell(modifier) { CastCardBody(vm, onOpenSetup) }
}

/**
 * The Stand's Flash/Cast slot (owner: "if both are enabled, they should
 * appear as a two piece button where Nooz Flash currently appears"). Both on
 * shares one card, split by a divider into two independently-tappable
 * pieces — stacked, not side-by-side, since either piece's own expanded
 * content (Flash's headline list, Cast's playback row) wants the card's full
 * width, not a half column. Either flag alone falls back to that feature's
 * own standalone card, unchanged from today; neither renders nothing, same
 * as the plain `if (noozFlashEnabled)` guard this replaced.
 */
@Composable
fun NoozBroadcastCard(
    vm: ReaderViewModel,
    noozFlashEnabled: Boolean,
    noozCastEnabled: Boolean,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        noozFlashEnabled && noozCastEnabled -> CardShell(modifier) {
            FlashCardBody(vm, onOpenSetup)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Tokens.Spacing.sm),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            CastCardBody(vm, onOpenSetup)
        }
        noozFlashEnabled -> FlashCard(vm = vm, onOpenSetup = onOpenSetup, modifier = modifier)
        noozCastEnabled -> CastCard(vm = vm, onOpenSetup = onOpenSetup, modifier = modifier)
    }
}

/**
 * Reads [text] aloud on tap via the device's own text-to-speech engine —
 * entirely on-device, no network. Shared by Nooz Flash's own "Play" (a ten-word
 * line) and the reader's per-article "listen" control (the full body text) —
 * same engine, same on/stop behaviour either way. Tapping again while
 * speaking stops it early.
 */
@Composable
fun PlayTextButton(text: String, playLabel: String, modifier: Modifier = Modifier) {
    val speaker = rememberTextSpeaker()
    // No speech engine on this machine (a bare desktop): show no control rather than one that cannot work.
    if (!speaker.available) return
    val speaking = speaker.speaking

    IconButton(
        modifier = modifier,
        onClick = { if (speaking) speaker.stop() else speaker.speak(text) },
    ) {
        Icon(
            if (speaking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (speaking) "Stop reading aloud" else playLabel,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Plays a rendered Nooz Cast narration file on tap (its own "Ready" state) — real playback of a
 * real synthesized file ([xyz.mdhv.riverwip.inference.local.LocalKokoroTtsProvider]'s own doc
 * comment covers the synthesis step upstream of this control).
 */
@Composable
private fun PlayAudioFileButton(audioPath: String, modifier: Modifier = Modifier) {
    val player = rememberAudioFilePlayer(audioPath)
    val playing = player.playing

    IconButton(modifier = modifier, onClick = { player.toggle() }) {
        Icon(
            if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Stop the narration" else "Play the narration",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
