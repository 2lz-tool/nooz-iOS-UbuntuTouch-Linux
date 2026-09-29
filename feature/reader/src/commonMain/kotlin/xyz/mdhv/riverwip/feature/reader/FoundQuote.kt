package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.ReadingAsideStyle

/**
 * A quiet aside every so often while reading (owner's ask, matching the web
 * reader's identical feature): a real sentence pulled from the article
 * actually open, never fabricated, never a reward -- no counter, no streak,
 * just an editorial break the way a long newspaper column gets a pull-quote.
 * The reading clock and picker live in [ReaderViewModel]; this file only
 * holds the picking logic and the two presentations.
 */
data class FoundQuote(val itemId: String, val text: String, val sourceTitle: String)

private const val MIN_LEN = 50
private const val MAX_LEN = 220
private val CAPS_RUN = Regex("[A-Z]{5,}")
private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+(?=[A-Z\"“])")

private fun sentencesFrom(paragraphs: List<String>): List<String> {
    val text = paragraphs.joinToString(" ") { it.trim() }.replace(Regex("\\s+"), " ").trim()
    if (text.isEmpty()) return emptyList()
    return text.split(SENTENCE_SPLIT)
        .map { it.trim() }
        .filter { it.length in MIN_LEN..MAX_LEN }
        .filter { !it.contains("http://") && !it.contains("https://") }
        .filter { !CAPS_RUN.containsMatchIn(it) } // skip all-caps runs (credits/bylines that slipped through)
}

/** Picks one real sentence from the article currently open. Null if nothing in it reads well set apart on its own -- no aside this cycle, rather than reaching for thin content. */
fun pickFoundQuote(itemId: String, sourceTitle: String?, paragraphs: List<String>): FoundQuote? {
    val sentences = sentencesFrom(paragraphs)
    if (sentences.isEmpty()) return null
    return FoundQuote(itemId, sentences.random(), sourceTitle ?: "Unknown source")
}

@Composable
fun FoundQuoteAside(quote: FoundQuote, style: ReadingAsideStyle, modifier: Modifier = Modifier) {
    if (style == ReadingAsideStyle.DATELINE) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(quote.sourceTitle.uppercase()) }
                append(" — “${quote.text}”")
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(modifier = Modifier.width(48.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(Res.string.reader_quote, quote.text),
            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = Tokens.Spacing.sm),
        )
        Text(
            quote.sourceTitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.width(48.dp).padding(top = Tokens.Spacing.sm),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
