package xyz.mdhv.riverwip.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import xyz.mdhv.riverwip.design.res.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A literal no-results state (owner, 2026-07: "attaching exclamation
 * illustration I want you to use when there are no results"): the owner's
 * own illustration, tinted to the surrounding theme (it's shipped as a plain
 * alpha mask so it reads correctly in white, paper, and dark), a fixed
 * headline, and one dry line picked from [NO_RESULTS_QUOTES] below it. This
 * is distinct from [EmptyState] — that one explains *why* a list is empty
 * (no sources added, nothing clipped yet) with actionable guidance; this one
 * is for the narrower case of a search or filter that came up genuinely
 * empty, where there's nothing to explain and nothing to do but say so.
 */
@Composable
fun NoResultsState(modifier: Modifier = Modifier, fill: Boolean = true) {
    val quote = remember { NO_RESULTS_QUOTES.random() }
    Column(
        modifier = (if (fill) modifier.fillMaxSize() else modifier).padding(Tokens.Spacing.xl),
        verticalArrangement = if (fill) Arrangement.Center else Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.img_no_results),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.height(72.dp),
        )
        Text(
            stringResource(Res.string.empty_no_results),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Spacing.lg),
        )
        Text(
            quote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Tokens.Spacing.xxs),
        )
    }
}

// Original, unattributed lines in the app's own dry voice — never a fabricated
// quote pinned to a real person. Picked once per time the state is entered,
// not re-rolled on every recomposition.
private val NO_RESULTS_QUOTES = listOf(
    "The wire stayed quiet on this one.",
    "Even omission has to omit something.",
    "Nothing here, which is, technically, the whole thesis.",
    "Not every search finds its story. This one didn't.",
    "The archives shrugged.",
    "Somewhere, a headline that isn't about this.",
    "You've reached the edge of what flowed.",
    "A blank page is still a page.",
    "Silence isn't a bug. Sometimes it's just silence.",
    "This particular nothing is, at least, honestly reported.",
)
