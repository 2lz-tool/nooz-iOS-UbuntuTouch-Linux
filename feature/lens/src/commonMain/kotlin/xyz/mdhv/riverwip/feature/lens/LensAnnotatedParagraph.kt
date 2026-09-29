package xyz.mdhv.riverwip.feature.lens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.model.AffectSpanDetector

// The owner's mark for loaded/strong language: a muted red, drawn as a *subtle*
// underline under otherwise-normal ink — never coloured link-blue text, which
// read as tappable hyperlinks (owner's note). Obscure words are no longer
// pre-marked at all; any word is long-pressed for its meaning, Kindle-style.
private val LoadedUnderline = Color(0xFFC0442F).copy(alpha = 0.6f)

/**
 * Cap on "Define X" accessibility actions offered per paragraph. TalkBack
 * reads the custom-action menu aloud in order, so this is a listening budget,
 * not a rendering one.
 */
private const val MAX_DEFINE_ACTIONS = 6

private data class RenderedMark(
    val span: AffectSpanDetector.Span,
    val renderedStart: Int,
    val renderedEnd: Int,
)

/**
 * A paragraph with the lens woven in (brief §P5 + owner's dictionary lens):
 *  - **Loaded language** gets a subtle red underline (gated by the "Highlight
 *    loaded language" setting); a tap opens its defuse sheet. The text stays in
 *    normal ink so it never looks like a hyperlink.
 *  - **Definitions** are Kindle-style: once a dictionary is downloaded,
 *    long-pressing *any* word opens its definition. Nothing is pre-underlined
 *    for this — the whole page is a dictionary.
 */
@Composable
fun LensAnnotatedParagraph(
    itemId: String,
    text: String,
    vm: LensViewModel,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    // Keyed on the Advanced-settings word-list customization too (not just
    // text): vm.detect() now reads vm.lensDisabledDefaultTerms/lensCustomTerms
    // internally, so a stale memo here would keep showing yesterday's marks
    // after either changes, even though nothing about `text` itself did.
    val affect = if (vm.underlinesEnabled) {
        remember(text, vm.lensDisabledDefaultTerms, vm.lensCustomTerms) { vm.detect(text) }
    } else {
        emptyList()
    }
    val defineEnabled = vm.dictionaryReady

    // Words worth defining, for the accessibility path below. Sighted readers
    // reach a definition by long-pressing *any* word; TalkBack cannot land a
    // press on a particular word inside a paragraph, so without this the
    // dictionary — and now the translation that shares its sheet — is simply
    // unreachable with a screen reader on. ObscureWords is the existing
    // detector for "which words in this paragraph would someone want defined",
    // and it had been left unused when pre-marking was dropped.
    val obscure = if (vm.obscureActive) {
        remember(text, vm.obscureActive) { vm.detectObscure(text) }
    } else {
        emptyList()
    }

    if (affect.isEmpty() && !defineEnabled) {
        Text(text = text, style = style, modifier = modifier)
        return
    }

    // Sterile-lens still applies to loaded language only.
    LaunchedEffect(itemId, text, vm.sterileLensEnabled) {
        if (vm.sterileLensEnabled) vm.defuseAll(itemId, text)
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selectedSpan by remember { mutableStateOf<AffectSpanDetector.Span?>(null) }
    var selectedWord by remember { mutableStateOf<String?>(null) }

    // All four verbs and both label templates are resolved here, in composable
    // scope. `semantics { }` is not one, and a custom action's label is the only
    // thing a screen-reader user hears about a mark -- so it is exactly the copy
    // that must not stay English.
    val revertVerb = stringResource(Res.string.defuse_revert_suggestion)
    val unavailableVerb = stringResource(Res.string.defuse_unavailable)
    val loadingVerb = stringResource(Res.string.defuse_loading_short)
    val viewVerb = stringResource(Res.string.defuse_view_suggestion)
    val spanActionTemplate = stringResource(Res.string.lens_span_action)
    val defineTemplate = stringResource(Res.string.lens_define_word)
    val spanActionLabel = { evidence: String, position: Int, total: Int, verb: String ->
        String.format(spanActionTemplate, evidence, position, total, verb)
    }
    val defineLabel = { word: String -> String.format(defineTemplate, word) }

    val rendered = remember(text, affect) { mutableListOf<RenderedMark>() }
    // Re-derive when any affect span's state changes (an accepted rewrite can
    // change length, so both the tap-mapping and the underline must move with it).
    val affectStates = affect.map { vm.stateFor(itemId, it) }
    val annotated = remember(text, affect, affectStates) {
        rendered.clear()
        buildAnnotatedString {
            var cursor = 0
            for (span in affect) {
                if (span.start > cursor) append(text.substring(cursor, span.start))
                val renderedStart = length
                when (val state = vm.stateFor(itemId, span)) {
                    is AffectSpanUiState.Accepted -> {
                        append(state.rewrittenText)
                        val dot = if (state.provenance == Provenance.NATIVE) {
                            Tokens.Color.provenanceNative
                        } else {
                            Tokens.Color.provenanceCloud
                        }
                        withStyle(SpanStyle(color = dot)) { append(" •") }
                    }
                    is AffectSpanUiState.Dismissed, is AffectSpanUiState.Rejected ->
                        append(text.substring(span.start, span.end))
                    // Untouched / Loading: plain ink; the subtle underline is
                    // drawn behind the text (see drawBehind below).
                    else -> append(text.substring(span.start, span.end))
                }
                rendered.add(RenderedMark(span, renderedStart, length))
                cursor = span.end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    val underlineStroke = with(androidx.compose.ui.platform.LocalDensity.current) { 1.3.dp.toPx() }

    Text(
        text = annotated,
        style = style,
        modifier = modifier
            .drawBehind {
                val lr = layoutResult ?: return@drawBehind
                for (r in rendered) {
                    // Only the still-loaded (un-defused) marks carry the underline.
                    val state = vm.stateFor(itemId, r.span)
                    if (state is AffectSpanUiState.Accepted ||
                        state is AffectSpanUiState.Dismissed ||
                        state is AffectSpanUiState.Rejected
                    ) {
                        continue
                    }
                    drawSpanUnderline(lr, r.renderedStart, r.renderedEnd, LoadedUnderline, underlineStroke)
                }
            }
            .pointerInput(rendered.size, defineEnabled) {
                detectTapGestures(
                    onLongPress = { pos ->
                        if (!defineEnabled) return@detectTapGestures
                        val lr = layoutResult ?: return@detectTapGestures
                        val offset = lr.getOffsetForPosition(pos)
                        val range = lr.getWordBoundary(offset)
                        val raw = annotated.text.substring(
                            range.start.coerceIn(0, annotated.text.length),
                            range.end.coerceIn(0, annotated.text.length),
                        )
                        val word = raw.trim { !it.isLetter() && it != '-' && it != '\'' }
                        if (word.length >= 2) selectedWord = word
                    },
                    onTap = { pos ->
                        val lr = layoutResult ?: return@detectTapGestures
                        val charOffset = lr.getOffsetForPosition(pos)
                        val hit = rendered.firstOrNull { charOffset in it.renderedStart until it.renderedEnd }
                        if (hit != null) selectedSpan = hit.span
                    },
                )
            }
            // TalkBack can't land a raw tap on an exact span; one custom action
            // per mark is the actual way in (brief §P7). Indexed so repeats stay
            // distinguishable.
            .semantics {
                val spanActions = rendered.mapIndexed { index, r ->
                    val verb = when (vm.stateFor(itemId, r.span)) {
                        is AffectSpanUiState.Accepted -> revertVerb
                        is AffectSpanUiState.Rejected -> unavailableVerb
                        is AffectSpanUiState.Loading -> loadingVerb
                        else -> viewVerb
                    }
                    CustomAccessibilityAction(
                        label = spanActionLabel(r.span.evidence, index + 1, rendered.size, verb),
                        action = { selectedSpan = r.span; true },
                    )
                }
                // The screen-reader equivalent of long-pressing a word. Named
                // rather than positional ("Define quotidian", not "Define word
                // three"), because a custom-action menu is read aloud in
                // sequence and a list of positions is unusable. Deduplicated
                // and capped: the menu is linear, and a paragraph with thirty
                // entries in it is worse than one with the first several.
                val defineActions = obscure
                    .map { it.word }
                    .distinct()
                    .take(MAX_DEFINE_ACTIONS)
                    .map { word ->
                        CustomAccessibilityAction(
                            label = defineLabel(word),
                            action = { selectedWord = word; true },
                        )
                    }
                customActions = spanActions + defineActions
            },
        onTextLayout = { layoutResult = it },
    )

    val currentSpan = selectedSpan
    if (currentSpan != null) {
        DefuseBottomSheet(
            itemId = itemId,
            span = currentSpan,
            originalText = text.substring(currentSpan.start, currentSpan.end),
            fullSentence = text,
            vm = vm,
            onDismissRequest = { selectedSpan = null },
        )
    }

    val currentWord = selectedWord
    if (currentWord != null) {
        DefinitionSheet(
            word = currentWord,
            vm = vm,
            onDismissRequest = { selectedWord = null },
        )
    }
}

/**
 * Draw a subtle underline under the rendered [start, end) range, line by line,
 * sitting just below the text baseline row. Kept off the glyphs so it reads as
 * emphasis, not a link.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpanUnderline(
    lr: TextLayoutResult,
    start: Int,
    end: Int,
    color: Color,
    stroke: Float,
) {
    if (end <= start) return
    val last = (end - 1).coerceAtLeast(start)
    val startLine = lr.getLineForOffset(start)
    val endLine = lr.getLineForOffset(last)
    for (line in startLine..endLine) {
        val ls = maxOf(start, lr.getLineStart(line))
        val le = minOf(end, lr.getLineEnd(line, visibleEnd = true))
        if (le <= ls) continue
        val x1 = lr.getHorizontalPosition(ls, usePrimaryDirection = true)
        val x2 = lr.getHorizontalPosition(le, usePrimaryDirection = true)
        val y = lr.getLineBottom(line) - stroke * 2f
        drawLine(color, Offset(x1, y), Offset(x2, y), strokeWidth = stroke)
    }
}
