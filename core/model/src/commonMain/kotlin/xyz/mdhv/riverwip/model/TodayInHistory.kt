package xyz.mdhv.riverwip.model

/**
 * One dated event from Wikipedia's "On this day" set. [url] is the Wikipedia
 * article the event's own summary links to, or null when the feed offered no
 * usable page for it.
 */
data class HistoricalEvent(
    val year: Int,
    val text: String,
    val url: String? = null,
)

/**
 * The pure half of Today in History (owner's ask, 2026-08: newspaper page
 * furniture, and the one of that batch that earns its place — the app already
 * shows what you missed across *sources*; this adds the same question across
 * *time*). Fetching and caching live in `:core:data`'s
 * `TodayInHistoryRepository`; the selection and text tidying are here, pure
 * and unit-tested, the same split [Diversifier] already follows.
 *
 * Deliberately never *links* an event to today's headlines. Asserting that a
 * 1971 currency decision "echoes" today's rate cut would be exactly the kind
 * of invented connection [FidelityGuard] exists to stop; the column sits
 * beside the news and lets the reader draw their own line, which is how the
 * rest of this app behaves (Contrast shows, it doesn't lecture).
 */
object TodayInHistory {

    /** How many events a column shows. A real paper's is a handful, not the feed's full fifteen. */
    const val DISPLAY_COUNT = 5

    /**
     * Wikipedia's blurbs are written to sit next to that day's illustration
     * ("Japan Air Lines Flight 123 (aircraft involved pictured) crashed..."),
     * and this column shows no illustration. Left in, the aside would point at
     * a picture that isn't there — so it comes out rather than being shown as
     * a dangling reference. Only image-referring parentheticals are touched;
     * ordinary ones (dates, clarifications) are content and stay.
     */
    private val IMAGE_ASIDE = Regex("""\s*\((?:[^()]*\b(?:pictured|depicted|illustrated)\b[^()]*)\)""", RegexOption.IGNORE_CASE)

    private val REPEATED_SPACE = Regex("""\s{2,}""")

    fun clean(text: String): String =
        IMAGE_ASIDE.replace(text, "")
            .replace(REPEATED_SPACE, " ")
            .replace(" .", ".")
            .replace(" ,", ",")
            .trim()

    /**
     * Picks the column from everything the feed returned. The feed arrives
     * newest-first and front-loaded with recent decades, so taking the first
     * few would make "today in history" mean "today in the last thirty
     * years". This samples evenly across the whole span instead — so a
     * column reaches back centuries, not just to living memory — then reads
     * oldest-first, the way a printed one does.
     *
     * Deterministic: the same day yields the same column every time it's
     * opened, never a reshuffle between glances (same rule [Diversifier]
     * holds itself to).
     */
    fun column(events: List<HistoricalEvent>, count: Int = DISPLAY_COUNT): List<HistoricalEvent> {
        if (count <= 0 || events.isEmpty()) return emptyList()
        if (events.size <= count) return events.sortedBy { it.year }
        if (count == 1) return listOf(events.first())

        // Evenly spaced indices across the full list, endpoints included, so
        // the oldest and most recent events both always make the cut.
        val picked = LinkedHashSet<Int>()
        for (i in 0 until count) {
            picked.add((i.toLong() * (events.size - 1) / (count - 1)).toInt())
        }
        return picked.map { events[it] }.sortedBy { it.year }
    }
}
