package xyz.mdhv.riverwip.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A quiet empty state. Empty states explain the honest denominator (brief §1):
 * this app never claims "all the news" — the denominator is always the user's
 * declared source-set.
 *
 * It leads with a faint emblem (owner, 2026-07: "empty states need something
 * nicer than just a line of text") — a blank sheet whose middle rule is a stub,
 * the app's own omission motif — so the state reads as a considered blank page,
 * not a stranded sentence. [fill] centres it in the whole surface (the default,
 * for full-screen empties); pass `false` to drop it inline in a scrolling flow.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = (if (fill) modifier.fillMaxSize() else modifier)
            .padding(Tokens.Spacing.xl),
        verticalArrangement = if (fill) Arrangement.Center else Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OmissionEmblem()
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Spacing.lg),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Spacing.xs),
        )
        if (action != null) {
            Column(Modifier.padding(top = Tokens.Spacing.md)) { action() }
        }
    }
}

/**
 * The omission mark: a blank sheet with three ruled lines where the middle rule
 * is cut short — the story that isn't there. Drawn faint so it frames the words
 * rather than competing with them, and it inherits the surface ink so it reads
 * on every theme.
 */
@Composable
private fun OmissionEmblem(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.size(56.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val faint = ink.copy(alpha = 0.30f)
            val fainter = ink.copy(alpha = 0.16f)

            val padX = size.width * 0.22f
            val padY = size.height * 0.12f
            val sheet = Size(size.width - 2 * padX, size.height - 2 * padY)
            drawRoundRect(
                color = faint,
                topLeft = Offset(padX, padY),
                size = sheet,
                cornerRadius = CornerRadius(size.width * 0.06f, size.width * 0.06f),
                style = Stroke(stroke),
            )

            val lineLeft = padX + sheet.width * 0.18f
            val lineRight = padX + sheet.width * 0.82f
            val fractions = floatArrayOf(0.40f, 0.55f, 0.70f)
            fractions.forEachIndexed { i, f ->
                val y = padY + sheet.height * f
                val isGap = i == 1
                drawLine(
                    color = if (isGap) fainter else faint,
                    start = Offset(lineLeft, y),
                    // The middle line stops short — the omitted story.
                    end = Offset(if (isGap) lineLeft + (lineRight - lineLeft) * 0.42f else lineRight, y),
                    strokeWidth = stroke,
                )
            }
        }
    }
}

@Preview(backgroundColor = 0xFFF7F6F3, showBackground = true)
@Composable
private fun EmptyStatePreview() {
    RiverTheme {
        EmptyState(
            title = "No sources yet",
            body = "This reader only shows ${Copy.fromSources(0)}. Add a feed to begin; " +
                "it never claims to show all the news.",
        )
    }
}
