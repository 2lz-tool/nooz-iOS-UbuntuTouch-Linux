package xyz.mdhv.riverwip.model

/**
 * Pure geometry for the day loom (owner's v7 reference, 2026-07): one tube per
 * topic. Every tube starts at the top with width ∝ its share of the day's
 * supply and plunges toward the waist; topics that were never read pinch out
 * to nothing there, while read topics pass through as narrow stems and fan
 * back out across the bottom with width ∝ their share of what was read.
 *
 * Coordinates live in the reference's own 420×880 viewBox; the Compose layer
 * scales uniformly and builds the actual cubic-bézier tube paths from each
 * band's [Band.stations]. The reference's topic list/colours/stem constants
 * were ad-libbed, so this generalises them: real taxonomy in, deterministic
 * spread out.
 */
object DayLoomLayout {

    const val W = 420.0
    const val H = 880.0
    const val MARGIN = 24.0
    const val WAIST_Y = 520.0
    const val GAP = 9.0

    /** Ease pairs from the reference: control-point fractions for the two tube segments. */
    val EASE_TOP = doubleArrayOf(0.5, 0.55)
    val EASE_BOT = doubleArrayOf(0.55, 0.5)

    data class Station(val y: Double, val x: Double, val w: Double)

    data class Band(
        val topic: Topic,
        val flowed: Int,
        val read: Int,
        val stations: List<Station>,
    ) {
        val consumed: Boolean get() = read > 0
    }

    data class Loom(
        /** Draw order: unread bands first, consumed bands after (largest read last, on top). */
        val bands: List<Band>,
        val totalFlowed: Int,
        val totalRead: Int,
    )

    fun layout(streamByTopic: Map<String, Int>, readByTopic: Map<String, Int>): Loom {
        val entries = Topic.entries.mapNotNull { topic ->
            val flowed = streamByTopic[topic.key] ?: 0
            if (flowed <= 0) null else Triple(topic, flowed, (readByTopic[topic.key] ?: 0).coerceIn(0, flowed))
        }
        val totalFlowed = entries.sumOf { it.second }
        val totalRead = entries.sumOf { it.third }
        if (totalFlowed == 0) return Loom(emptyList(), 0, 0)

        val usable = W - MARGIN * 2 - GAP * (entries.size - 1)
        val consumedList = entries.filter { it.third > 0 }.sortedByDescending { it.third }

        // Bottom fan: read topics spread across the width, widest first, with
        // width ∝ share of reads (the reference's 262px fan budget).
        val fanBudget = 262.0
        val fanGap = 24.0
        val fanWidths = consumedList.map { (_, _, read) -> (read.toDouble() / totalRead) * fanBudget }
        val fanTotal = fanWidths.sum() + fanGap * (consumedList.size - 1).coerceAtLeast(0)
        var fanCursor = W / 2 - fanTotal / 2
        val botCenter = HashMap<Topic, Double>()
        val botWidth = HashMap<Topic, Double>()
        consumedList.forEachIndexed { i, (topic, _, _) ->
            botCenter[topic] = fanCursor + fanWidths[i] / 2
            botWidth[topic] = fanWidths[i]
            fanCursor += fanWidths[i] + fanGap
        }

        // Stems: consumed tubes pass the waist as thin offset threads.
        val stemSpread = 3.0
        val stemOffset = HashMap<Topic, Double>()
        val stemWidth = HashMap<Topic, Double>()
        consumedList.forEachIndexed { i, (topic, _, read) ->
            val t = if (consumedList.size == 1) 0.5 else i.toDouble() / (consumedList.size - 1)
            stemOffset[topic] = -stemSpread + t * 2 * stemSpread
            stemWidth[topic] = 1.5 + 2.0 * (read.toDouble() / (consumedList[0].third))
        }

        var cursor = MARGIN
        val bands = entries.map { (topic, flowed, read) ->
            val topW = ((flowed.toDouble() / totalFlowed) * usable).coerceAtLeast(3.0)
            val topX = cursor + topW / 2
            cursor += topW + GAP
            val stations = if (read > 0) {
                listOf(
                    Station(0.0, topX, topW),
                    Station(WAIST_Y, W / 2 + stemOffset.getValue(topic), stemWidth.getValue(topic)),
                    Station(H, botCenter.getValue(topic), botWidth.getValue(topic)),
                )
            } else {
                // Unread: pinch out just past the waist, drifting slightly toward centre.
                val drift = ((topX - W / 2) / (W / 2)) * 4.0
                listOf(
                    Station(0.0, topX, topW),
                    Station(WAIST_Y, W / 2 + drift, 0.5),
                )
            }
            Band(topic, flowed, read, stations)
        }

        // Reference draw order: unread first, then consumed ascending by read
        // so the largest consumed band lands on top.
        val ordered = bands.filter { !it.consumed } + bands.filter { it.consumed }.sortedBy { it.read }
        return Loom(ordered, totalFlowed, totalRead)
    }

    /**
     * The compressed loom — the Stand/reader's multi-colour day bar: one
     * segment per topic with items today, ordered like the loom's top edge.
     */
    fun dayMix(streamByTopic: Map<String, Int>): List<Pair<Topic, Double>> {
        val total = streamByTopic.values.filter { it > 0 }.sum()
        if (total == 0) return emptyList()
        return Topic.entries.mapNotNull { topic ->
            val n = streamByTopic[topic.key] ?: 0
            if (n > 0) topic to n.toDouble() / total else null
        }
    }
}

/** Compact count label for the loom's totals: 950 → "950", 10_000 → "10k", 12_345 → "12.3k". */
fun formatCompactCount(count: Int): String = when {
    count < 1_000 -> count.toString()
    count < 1_000_000 -> {
        val tenths = count / 100 // count in units of 0.1k
        if (tenths % 10 == 0) "${tenths / 10}k" else "${tenths / 10}.${tenths % 10}k"
    }
    else -> {
        val tenths = count / 100_000
        if (tenths % 10 == 0) "${tenths / 10}M" else "${tenths / 10}.${tenths % 10}M"
    }
}
