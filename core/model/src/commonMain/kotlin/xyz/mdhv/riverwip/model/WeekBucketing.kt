package xyz.mdhv.riverwip.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Period bucketing for the river's weekly abstraction (brief §P4: "period is a
 * parameter; ship weekly" — daily-vs-weekly default is a logged open question,
 * STATE.md §10). Pure and deterministic: UTC, Monday-start ISO weeks.
 *
 * Kept in `:core:model` (not `:core:data`) because [WeeklyAggregate] itself lives
 * here and the aggregation pipeline needs a single, testable definition of "which
 * period does this timestamp belong to".
 */
object WeekBucketing {

    /** Default period length in days. Weekly per brief §P4; parameterized for the logged daily option. */
    const val DEFAULT_PERIOD_DAYS = 7

    /**
     * The start (epoch millis, UTC midnight) of the period containing [epochMillis].
     * For the default 7-day period this is the Monday of that UTC week.
     */
    fun periodStart(epochMillis: Long, periodDays: Int = DEFAULT_PERIOD_DAYS): Long {
        require(periodDays > 0) { "periodDays must be positive" }
        val day = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val epochDay = day.toEpochDay() // days since 1970-01-01 (a Thursday)
        // Align periods to ISO-week Mondays for periodDays == 7; for other
        // lengths, bucket from the epoch directly (still deterministic/stable).
        val bucketStartEpochDay = if (periodDays == 7) {
            // 1970-01-01 (epochDay 0) was a Thursday: ISO dayOfWeek(d) = ((d+3) mod 7) + 1
            // (Mon=1..Sun=7), so daysSinceMonday(d) = (d+3) mod 7.
            val daysSinceMonday = ((epochDay + 3) % 7 + 7) % 7
            epochDay - daysSinceMonday
        } else {
            epochDay - (epochDay.floorModPositive(periodDays.toLong()))
        }
        return Instant.EPOCH.plus(bucketStartEpochDay, ChronoUnit.DAYS).toEpochMilli()
    }

    private fun Long.floorModPositive(m: Long): Long = ((this % m) + m) % m

    /** The [periodStart, periodEnd) half-open range containing [epochMillis]. */
    fun periodRange(epochMillis: Long, periodDays: Int = DEFAULT_PERIOD_DAYS): LongRange {
        val start = periodStart(epochMillis, periodDays)
        val end = Instant.ofEpochMilli(start).plus(periodDays.toLong(), ChronoUnit.DAYS).toEpochMilli()
        return start until end
    }
}
