package xyz.mdhv.riverwip.model

/**
 * UTC calendar arithmetic and the three date formats feeds use, replacing the
 * `java.time` calls that used to live in [FeedParser], [FeedUrls] and
 * [WeekBucketing] so this module builds for Kotlin/Native too.
 *
 * Everything is UTC epoch-millisecond in, epoch-millisecond out. The parsers
 * accept exactly what the `java.time` formatters they replace accepted
 * (`RFC_1123_DATE_TIME`, `ISO_OFFSET_DATE_TIME`, the GDELT pattern) so already
 * ingested feeds keep dating the same way; anything else is `null`.
 */
internal object CivilTime {

    const val MILLIS_PER_DAY = 86_400_000L

    /** Days since 1970-01-01 for a proleptic-Gregorian date (Howard Hinnant's `days_from_civil`). */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = floorDiv(y.toLong(), 400)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    /** Inverse of [daysFromCivil]: (year, month, day). */
    fun civilFromDays(epochDay: Long): Triple<Int, Int, Int> {
        val z = epochDay + 719_468
        val era = floorDiv(z, 146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        return Triple((if (m <= 2) y + 1 else y).toInt(), m, d)
    }

    fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
    }

    fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    private fun daysInMonth(y: Int, m: Int) = when (m) {
        2 -> if (isLeap(y)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun epochMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, millis: Int, offsetSeconds: Int): Long =
        (daysFromCivil(y, mo, d) * 86_400L + h * 3_600L + mi * 60L + s - offsetSeconds) * 1_000L + millis

    // ---- formatting ------------------------------------------------------

    /** `yyyyMMddHHmmss` in UTC (the GDELT query-parameter format). */
    fun formatCompactUtc(epochMillis: Long): String {
        val day = floorDiv(epochMillis, MILLIS_PER_DAY)
        val msOfDay = floorMod(epochMillis, MILLIS_PER_DAY)
        val (y, mo, d) = civilFromDays(day)
        val h = (msOfDay / 3_600_000).toInt()
        val mi = ((msOfDay / 60_000) % 60).toInt()
        val s = ((msOfDay / 1_000) % 60).toInt()
        return "${y.toString().padStart(4, '0')}${two(mo)}${two(d)}${two(h)}${two(mi)}${two(s)}"
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    // ---- parsing ---------------------------------------------------------

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    /** RSS dates: `[Wed, ]02 Oct 2024 13:00[:00] GMT|+0530`. Weekday, when present, must agree with the date. */
    fun parseRfc1123(raw: String): Long? {
        var s = raw
        var dayOfWeek: Int? = null
        val comma = s.indexOf(',')
        if (comma >= 0) {
            val name = s.substring(0, comma)
            dayOfWeek = DAYS.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 && name.length == 3 } ?: return null
            s = s.substring(comma + 1)
            if (!s.startsWith(" ")) return null
            s = s.substring(1)
        }
        val parts = s.split(' ')
        if (parts.size != 5) return null
        val day = parts[0].takeIf { it.length in 1..2 && it.all(Char::isAsciiDigit) }?.toInt() ?: return null
        val month = MONTHS.indexOfFirst { it.equals(parts[1], ignoreCase = true) }.takeIf { it >= 0 && parts[1].length == 3 }?.plus(1) ?: return null
        val year = parts[2].takeIf { it.length == 4 && it.all(Char::isAsciiDigit) }?.toInt() ?: return null
        val time = parts[3].split(':')
        // java.time's RFC_1123 formatter parses leniently: 1- or 2-digit time fields ("9:05:00") are fine.
        if (time.size !in 2..3 || time.any { it.length !in 1..2 || !it.all(Char::isAsciiDigit) }) return null
        val hour = time[0].toInt()
        val minute = time[1].toInt()
        val second = time.getOrNull(2)?.toInt() ?: 0
        // SMART resolution: 24:00:00 is midnight at the end of that day.
        if (hour > 24 || minute > 59 || second > 59 || (hour == 24 && (minute != 0 || second != 0))) return null

        val zone = parts[4]
        val offset = when {
            zone.equals("GMT", ignoreCase = true) -> 0
            zone.length == 5 && (zone[0] == '+' || zone[0] == '-') && zone.substring(1).all(Char::isAsciiDigit) -> {
                val oh = zone.substring(1, 3).toInt()
                val om = zone.substring(3, 5).toInt()
                if (oh > 18 || om > 59) return null
                (if (zone[0] == '-') -1 else 1) * (oh * 3_600 + om * 60)
            }
            else -> return null
        }
        // java.time's RFC_1123 formatter resolves SMART: 29-31 past the month's end clamps to it; anything else is invalid.
        if (day !in 1..31) return null
        val clamped = minOf(day, daysInMonth(year, month))
        if (dayOfWeek != null) {
            val actual = floorMod(daysFromCivil(year, month, clamped) + 3, 7).toInt() // 1970-01-01 was a Thursday (index 3)
            if (actual != dayOfWeek) return null
        }
        return epochMillis(year, month, clamped, hour, minute, second, 0, offset)
    }

    /**
     * Atom dates: `2024-10-02T13:00:00Z`, `2024-10-02T13:00:00.123+05:30`. The
     * `[Region/Id]` suffix of Java's zoned form is not accepted: feeds never emit it and
     * validating a region name would need a time-zone database.
     */
    fun parseIsoOffset(raw: String): Long? {
        val s = raw
        if (s.length < 17 || s[4] != '-' || s[7] != '-' || (s[10] != 'T' && s[10] != 't')) return null
        val year = s.substring(0, 4).asAsciiInt() ?: return null
        val month = s.substring(5, 7).asAsciiInt() ?: return null
        val day = s.substring(8, 10).asAsciiInt() ?: return null
        if (month !in 1..12 || day !in 1..daysInMonth(year, month)) return null
        if (s[13] != ':') return null
        val hour = s.substring(11, 13).asAsciiInt() ?: return null
        val minute = s.substring(14, 16).asAsciiInt() ?: return null
        var i = 16
        var second = 0
        var millis = 0
        if (i < s.length && s[i] == ':') {
            if (i + 3 > s.length) return null
            second = s.substring(i + 1, i + 3).asAsciiInt() ?: return null
            i += 3
            if (i < s.length && s[i] == '.') {
                var j = i + 1
                while (j < s.length && s[j].isAsciiDigit()) j++
                val digits = s.substring(i + 1, j)
                if (digits.isEmpty() || digits.length > 9) return null
                millis = digits.padEnd(3, '0').substring(0, 3).toInt()
                i = j
            }
        }
        if (hour > 23 || minute > 59 || second > 59) return null

        val offset: Int = when {
            i >= s.length -> return null
            s[i] == 'Z' || s[i] == 'z' -> { if (i + 1 != s.length) return null; 0 }
            s[i] == '+' || s[i] == '-' -> {
                val sign = if (s[i] == '-') -1 else 1
                val rest = s.substring(i + 1)
                if (rest.length != 5 && rest.length != 8) return null
                if (rest[2] != ':') return null
                val oh = rest.substring(0, 2).asAsciiInt() ?: return null
                val om = rest.substring(3, 5).asAsciiInt() ?: return null
                var os = 0
                if (rest.length == 8) {
                    if (rest[5] != ':') return null
                    os = rest.substring(6, 8).asAsciiInt() ?: return null
                }
                if (oh > 18 || om > 59 || os > 59) return null
                sign * (oh * 3_600 + om * 60 + os)
            }
            else -> return null
        }
        return epochMillis(year, month, day, hour, minute, second, millis, offset)
    }

    /** GDELT `seendate`: `yyyyMMdd'T'HHmmss'Z'`, UTC. */
    fun parseGdelt(s: String): Long? {
        if (s.length != 16 || s[8] != 'T' || s[15] != 'Z') return null
        val year = s.substring(0, 4).asAsciiInt() ?: return null
        val month = s.substring(4, 6).asAsciiInt() ?: return null
        val day = s.substring(6, 8).asAsciiInt() ?: return null
        val hour = s.substring(9, 11).asAsciiInt() ?: return null
        val minute = s.substring(11, 13).asAsciiInt() ?: return null
        val second = s.substring(13, 15).asAsciiInt() ?: return null
        if (month !in 1..12 || day < 1 || hour > 24 || minute > 59 || second > 59) return null
        // Pattern-based java.time parsing (SMART) reads 24:00:00 as midnight at the end of that day.
        if (hour == 24 && (minute != 0 || second != 0)) return null
        return epochMillis(year, month, minOf(day, daysInMonth(year, month)), hour, minute, second, 0, 0)
    }

    private fun String.asAsciiInt(): Int? = if (isNotEmpty() && all { it in '0'..'9' }) toInt() else null
}

private fun Char.isAsciiDigit() = this in '0'..'9'
