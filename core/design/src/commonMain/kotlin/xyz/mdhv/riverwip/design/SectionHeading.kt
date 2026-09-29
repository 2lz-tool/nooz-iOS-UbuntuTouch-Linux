package xyz.mdhv.riverwip.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp

/**
 * A sub-heading inside a settings-style screen ("Theme", "Font", "Reader
 * intelligence", "One-click starters" …). Previously each was plain
 * body-sized text in a muted colour — the same size and weight as the rows
 * underneath it, just greyer, so it read as *less* prominent than its own
 * content instead of more (owner's #9: headings weren't standing out enough
 * to scan by). Set caps + letter-spaced instead, the same treatment the
 * app's own chrome labels ("SETTINGS", "EDIT") already use — a distinct
 * typographic voice for "this is a label," independent of size or contrast.
 */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
        color = color,
        modifier = modifier,
    )
}

@Preview(backgroundColor = 0xFFF7F6F3, showBackground = true)
@Composable
private fun SectionHeadingPreview() {
    RiverTheme {
        SectionHeading("Reader intelligence")
    }
}
