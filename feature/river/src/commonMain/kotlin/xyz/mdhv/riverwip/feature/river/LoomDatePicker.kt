package xyz.mdhv.riverwip.feature.river

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM")
private val DAY_DESC = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val DOW = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

/**
 * The loom's date picker (owner's mock): a vertically scrolling run of months
 * — Sunday-first grid. Today is a filled circle with light text; the standing
 * selection (a single day, or a range's two endpoints) is outlined instead —
 * the owner's own correction, since a plain fill for "selected" and an
 * outline for "today" made every past day look selected once you'd looked at
 * it. Months span the Item retention window, so every pickable day can
 * actually have data.
 *
 * A "Range" chip switches the two-tap flow (owner's #4): the first tap marks
 * a pending start (shown outlined, same as any endpoint); the second commits
 * the range via [onPickRange], order-independent — tapping the same day
 * twice cancels the pending start instead of picking a zero-length range.
 */
@Composable
fun LoomDatePicker(
    days: List<xyz.mdhv.riverwip.model.WeeklyAggregate>,
    selectedDayStart: Long?,
    selectedRangeEnd: Long?,
    onPick: (Long) -> Unit,
    onPickRange: (Long, Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val selectedStart = selectedDayStart?.let { localDate(it, zone) }
    val selectedEnd = selectedRangeEnd?.let { localDate(it, zone) }
    var rangeMode by remember { mutableStateOf(false) }
    var pendingRangeStart by remember { mutableStateOf<LocalDate?>(null) }

    val firstMonth = remember(days) {
        val earliest = days.firstOrNull()?.weekStart?.let { localDate(it, zone) } ?: today.minusDays(60)
        YearMonth.from(minOf(earliest, today.minusDays(60)))
    }
    val months = remember(firstMonth, today) {
        generateSequence(firstMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(today)) }
            .toList()
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (months.size - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoozWordmark(fontSize = 30.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (rangeMode && pendingRangeStart != null) "Pick the other end" else "Pick a day",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
        ) {
            FilterChip(
                selected = !rangeMode,
                onClick = { rangeMode = false; pendingRangeStart = null },
                label = { Text(stringResource(Res.string.loom_single_day)) },
            )
            FilterChip(
                selected = rangeMode,
                onClick = { rangeMode = true },
                label = { Text(stringResource(Res.string.loom_range)) },
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Spacing.md)) {
            for (d in DOW) {
                Text(
                    d,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(months.size) { i ->
                MonthGrid(
                    month = months[i],
                    today = today,
                    selectedStart = selectedStart,
                    selectedEnd = selectedEnd,
                    pendingRangeStart = pendingRangeStart,
                    onPick = { date ->
                        when {
                            !rangeMode -> onPick(date.atStartOfDay(zone).toInstant().toEpochMilli())
                            pendingRangeStart == null -> pendingRangeStart = date
                            pendingRangeStart == date -> pendingRangeStart = null
                            else -> {
                                onPickRange(
                                    pendingRangeStart!!.atStartOfDay(zone).toInstant().toEpochMilli(),
                                    date.atStartOfDay(zone).toInstant().toEpochMilli(),
                                )
                                pendingRangeStart = null
                            }
                        }
                    },
                )
            }
        }
    }
}

private fun localDate(millis: Long, zone: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedStart: LocalDate?,
    selectedEnd: LocalDate?,
    pendingRangeStart: LocalDate?,
    onPick: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Spacing.md)) {
        SectionHeading(
            MONTH_FORMAT.format(month.atDay(1)),
            modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.sm),
        )
        val firstDow = month.atDay(1).dayOfWeek.value % 7 // Sunday-first column index
        val daysInMonth = month.lengthOfMonth()
        var day = 1 - firstDow
        while (day <= daysInMonth) {
            Row(Modifier.fillMaxWidth()) {
                repeat(7) {
                    val d = day + it
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (d in 1..daysInMonth) {
                            val date = month.atDay(d)
                            val isToday = date == today
                            val isEndpoint = date == selectedStart || date == selectedEnd || date == pendingRangeStart
                            val isInRange = selectedStart != null && selectedEnd != null &&
                                date > selectedStart && date < selectedEnd
                            val isFuture = date.isAfter(today)
                            val dayLabel = DAY_DESC.format(date) +
                                when {
                                    isEndpoint -> ", selected"
                                    isFuture -> ", not yet available"
                                    else -> ""
                                }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(Tokens.Spacing.xxs)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isToday -> MaterialTheme.colorScheme.onBackground
                                            isInRange -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                                            else -> MaterialTheme.colorScheme.background
                                        },
                                    )
                                    .then(
                                        if (isEndpoint && !isToday) {
                                            Modifier.border(Tokens.Border.thin, MaterialTheme.colorScheme.onBackground, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .selectable(
                                        selected = isEndpoint,
                                        enabled = !isFuture,
                                        role = Role.Button,
                                        onClick = { onPick(date) },
                                    )
                                    .semantics { contentDescription = dayLabel },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    d.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isToday -> MaterialTheme.colorScheme.background
                                        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.onBackground
                                    },
                                )
                            }
                        }
                    }
                }
            }
            day += 7
        }
    }
}
