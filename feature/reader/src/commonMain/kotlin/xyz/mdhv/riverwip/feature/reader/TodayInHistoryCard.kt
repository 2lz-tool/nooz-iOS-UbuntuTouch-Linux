package xyz.mdhv.riverwip.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.mdhv.riverwip.data.repo.TodayInHistoryRepository
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.model.HistoricalEvent

/**
 * Today in History (owner's ask, 2026-08): a short dated column standing
 * above the day's stories, the way a printed paper's does.
 *
 * Sits *beside* the news and never claims a link to it. The app already
 * shows what a reader missed across sources; this asks the same question
 * across time, and leaves the connecting to the reader (see
 * [xyz.mdhv.riverwip.model.TodayInHistory]'s own note on why nothing here
 * tries to tie an event to today's headlines).
 *
 * Callers gate on the setting before composing this at all (see
 * [ArticleListScreen]), so a reader who left the feature off never reaches
 * the fetch below.
 */
@Composable
fun TodayInHistoryCard(
    vm: ReaderViewModel,
    modifier: Modifier = Modifier,
) {
    val state by vm.historyState.collectAsStateWithLifecycle()

    // The fetch starts here rather than in the view model's init, so that a
    // reader with the feature off never triggers it at all.
    LaunchedEffect(Unit) { vm.loadTodayInHistory() }

    // A failed or empty day collapses the card entirely rather than parking a
    // dead box above the news: this is page furniture, and furniture that has
    // nothing to say should get out of the way. The one exception is a
    // reachability failure, which stays as a single tappable line so a reader
    // who was offline has a way back.
    when (val s = state) {
        is HistoryUiState.Idle, is HistoryUiState.Loading -> Unit
        is HistoryUiState.Unavailable -> HistoryNotice(s.reason, onRetry = vm::retryTodayInHistory, modifier = modifier)
        is HistoryUiState.Ready -> HistoryColumn(s.events, modifier = modifier)
    }
}

@Composable
private fun HistoryShell(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xs)
            .clip(RoundedCornerShape(Tokens.Radius.md))
            .border(BorderStroke(Tokens.Border.thin, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(Tokens.Radius.md))
            .padding(Tokens.Spacing.sm),
        content = content,
    )
}

@Composable
private fun HistoryColumn(events: List<HistoricalEvent>, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    HistoryShell(modifier) {
        SectionHeading(stringResource(Res.string.settings_today_in_history), color = MaterialTheme.colorScheme.onBackground)
        for ((index, event) in events.withIndex()) {
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = Tokens.Spacing.xxs),
                )
            }
            // Bound to a local first: `url` is a public val from :core:model,
            // and Kotlin won't smart-cast a property across a module boundary.
            val url = event.url
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // Only rows that actually have somewhere to go are
                        // clickable, so a tap never silently does nothing.
                        if (url != null) {
                            Modifier.clickable(onClickLabel = stringResource(Res.string.history_read_year, event.year.toString())) {
                                openLink(uriHandler, url)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = Tokens.Spacing.xxs),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
            ) {
                // A fixed year gutter, so the dates line up as a column down
                // the left edge the way a printed almanac's do, instead of
                // ragging with each line's text length.
                Text(
                    event.year.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(YEAR_GUTTER),
                )
                Text(
                    event.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Wikipedia's text is CC BY-SA, so the credit sits in view under the
        // column itself, not buried on an About screen.
        Text(
            TodayInHistoryRepository.ATTRIBUTION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClickLabel = stringResource(Res.string.history_open_wikipedia)) {
                    openLink(uriHandler, TodayInHistoryRepository.SOURCE_URL)
                }
                .padding(top = Tokens.Spacing.xxs),
        )
    }
}

@Composable
private fun HistoryNotice(reason: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    HistoryShell(modifier) {
        SectionHeading(stringResource(Res.string.settings_today_in_history), color = MaterialTheme.colorScheme.onBackground)
        Text(
            stringResource(Res.string.history_retry_notice, reason),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(Res.string.history_try_again)) { onRetry() }
                .padding(top = Tokens.Spacing.xxs),
        )
    }
}

/** A missing browser is a real device state, not a crash: the tap just does nothing rather than taking the app down. */
private fun openLink(uriHandler: UriHandler, url: String) {
    try {
        uriHandler.openUri(url)
    } catch (_: Exception) {
        // No browser installed / no handler for the link.
    }
}

private val YEAR_GUTTER = 44.dp
