package xyz.mdhv.riverwip.design

/**
 * Copy helpers that enforce the register constraints (brief §1, §7):
 *  - Denominator honesty on every total: the denominator is always the user's
 *    declared source-set, never "all the news".
 *  - Descriptive, never scolding. Shapes and counts, no FOMO, no praise.
 *
 * Centralising the phrasing here keeps every surface consistent and makes the
 * register auditable in one place.
 */
object Copy {

    /** "from the N sources you chose" — appended to any stream total. */
    fun fromSources(sourceCount: Int): String = when (sourceCount) {
        0 -> "from the sources you choose"
        1 -> "from the 1 source you chose"
        else -> "from the $sourceCount sources you chose"
    }

    /**
     * A stream total, framed descriptively. e.g.
     * "412 politics stories flowed past this week; you opened 9".
     * Never "you missed 400 stories".
     */
    fun flowed(count: Int, topicLabel: String, period: String): String =
        "$count $topicLabel ${if (count == 1) "story" else "stories"} flowed past $period"

    fun openedOf(opened: Int): String = "you opened $opened"

    /** Detection is framed as opinion, never verdict (brief §7). */
    const val DETECTION_IS_OPINION = "the app's read, tap to see why"

    /** Feeds end; the river has banks (brief §3). */
    const val END_OF_FEED = "that's everything from your sources for this period"
}
