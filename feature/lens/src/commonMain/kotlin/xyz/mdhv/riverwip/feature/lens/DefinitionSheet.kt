package xyz.mdhv.riverwip.feature.lens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.DictionaryFormatting

private sealed interface DefState {
    data object Looking : DefState
    data object NotFound : DefState
    data class Found(val senses: List<DictionaryFormatting.Sense>) : DefState
}

// A domain label at the very start of a sense, e.g. "(Com.)", "(Law)" — set
// in italics like the reference dictionary does for its field tags.
private val LEADING_DOMAIN_TAG = Regex("""^(\([A-Za-z.& ]+\))\s*""")

/**
 * The dictionary lens's definition sheet (owner's #9: "needs to look like an
 * actual dictionary" — the bundled Webster's 1913 text arrives as one flat,
 * unbroken run with no line breaks at all). [DictionaryFormatting] recovers
 * the entry's real structure — numbered senses, part-of-speech groups, "Syn.
 * --" cross-references — and this sheet lays each out the way a paper
 * dictionary would: a bold sense number, a paragraph break between parts of
 * speech, synonym blocks and domain tags in italics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefinitionSheet(
    word: String,
    vm: LensViewModel,
    onDismissRequest: () -> Unit,
) {
    var state by remember(word) { mutableStateOf<DefState>(DefState.Looking) }
    LaunchedEffect(word) {
        val definition = vm.define(word)?.trim()?.replace(Regex("\\s+"), " ")
        state = if (definition.isNullOrBlank()) {
            DefState.NotFound
        } else {
            DefState.Found(DictionaryFormatting.parse(definition))
        }
    }

    // The translation, if the reader installed a bilingual dictionary (owner's
    // ask: reading in a second or third language, long-press a word and see it
    // in your own). Loaded separately from the definition on purpose — the two
    // are independent downloads, and a reader who has only one of them should
    // still get everything that one can answer.
    val targetName = vm.translationTargetName
    var translations by remember(word, targetName) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(word, targetName) {
        translations = if (targetName == null) emptyList() else vm.translate(word)
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .padding(Tokens.Spacing.md)
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            // Serif headword — the newspaper/reading voice (headlineSmall is serif by role).
            Text(word, style = MaterialTheme.typography.headlineSmall)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = Tokens.Spacing.xs, bottom = Tokens.Spacing.sm),
            )
            when (val s = state) {
                DefState.Looking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.lens_looking_up), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DefState.NotFound -> Text(
                    stringResource(Res.string.lens_no_definition),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is DefState.Found -> Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs)) {
                    for (sense in s.senses) {
                        SenseRow(sense)
                    }
                }
            }

            // Translations sit under the definition, labelled with the language
            // so the reader knows which of theirs they are looking at. Rendered
            // only when a bilingual dictionary is installed *and* it had an
            // entry: an empty "Spanish" heading would read as a failure.
            val senses = translations
            if (targetName != null && !senses.isNullOrEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = Tokens.Spacing.sm, bottom = Tokens.Spacing.xs),
                )
                Text(
                    targetName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    senses.joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = Tokens.Spacing.xxs),
                )
            }
        }
    }
}

/**
 * One sense (or synonym block), as one [Text] with a hanging indent — a
 * wrapped continuation line falls under the body text, not back under the
 * sense number, the way a printed dictionary sets it.
 */
@Composable
private fun SenseRow(sense: DictionaryFormatting.Sense) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onBackground
    val topSpace = if (sense.startsNewGroup) Tokens.Spacing.sm else 0.dp
    // Only a "1. " or "Syn.: " lead-in pushes the first line in; a lone
    // unnumbered sense (the whole entry is one Sense with no marker at all)
    // has nothing for a hanging indent to hang from, so its wrapped lines
    // must stay flush with the first line instead of drifting right of it.
    val hangingIndent = TextIndent(restLine = if (sense.number != null || sense.isSynonymBlock) 22.sp else 0.sp)

    if (sense.isSynonymBlock) {
        Text(
            buildAnnotatedString {
                withStyle(ParagraphStyle(textIndent = hangingIndent)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)) { append("Syn.: ") }
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(sense.text) }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            modifier = Modifier.padding(top = topSpace),
        )
        return
    }

    val tagMatch = LEADING_DOMAIN_TAG.find(sense.text)
    val body = if (tagMatch != null) sense.text.substring(tagMatch.range.last + 1) else sense.text

    Text(
        buildAnnotatedString {
            withStyle(ParagraphStyle(textIndent = hangingIndent)) {
                if (sense.number != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${sense.number}. ") }
                }
                if (tagMatch != null) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = muted)) { append(tagMatch.groupValues[1]) }
                    append(' ')
                }
                append(body)
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = ink,
        modifier = Modifier.padding(top = topSpace),
    )
}
