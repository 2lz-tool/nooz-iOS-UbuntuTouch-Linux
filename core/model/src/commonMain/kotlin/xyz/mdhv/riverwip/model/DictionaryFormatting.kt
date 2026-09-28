package xyz.mdhv.riverwip.model

/**
 * Splits a raw Webster's-1913-style definition string into its numbered
 * senses, for dictionary-style rendering (owner's #9: "the dictionary result
 * needs to look like an actual dictionary" — the source lexicon is one flat
 * run of text with no line breaks at all, so every sense and part-of-speech
 * ran together). Recognizes the two structural markers this dataset actually
 * uses, since it carries neither an explicit part-of-speech tag nor a
 * pronunciation:
 *  - a leading "N. " sense number, which **restarts at 1** for each new part
 *    of speech (Webster's own convention — it's the only signal a new group
 *    started at all);
 *  - a "Syn. -- …" cross-reference block, which always closes out a group and
 *    carries no number of its own.
 *
 * Pure and deterministic; never invents a part-of-speech or pronunciation
 * this dataset doesn't actually carry.
 */
object DictionaryFormatting {

    data class Sense(
        /** Null for a lead-in fragment before any numbered sense, or a Syn. block. */
        val number: Int?,
        val text: String,
        /** The first sense after a numbering restart — a new part-of-speech group starts here. */
        val startsNewGroup: Boolean,
        val isSynonymBlock: Boolean,
    )

    // A sense marker is a small number + period + space, immediately after a
    // sentence-ending "." or ";" (or at the very start of the text) — never
    // mid-sentence, so "3 bills" or "3.5%" in running prose can't false-match.
    private val SENSE_MARKER = Regex("""(?:^|(?<=[.;]\s))(\d{1,2})\.\s+""")
    private val SYN_MARKER = Regex("""(?:^|(?<=[.;]\s))Syn\.\s*--\s*""")

    fun parse(raw: String): List<Sense> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()

        data class Marker(val start: Int, val contentStart: Int, val number: Int?)

        val markers = buildList {
            for (m in SENSE_MARKER.findAll(text)) {
                add(Marker(m.range.first, m.range.last + 1, m.groupValues[1].toIntOrNull()))
            }
            for (m in SYN_MARKER.findAll(text)) {
                add(Marker(m.range.first, m.range.last + 1, null))
            }
        }.sortedBy { it.start }

        if (markers.isEmpty()) {
            return listOf(Sense(number = null, text = text, startsNewGroup = false, isSynonymBlock = false))
        }

        val senses = mutableListOf<Sense>()
        val lead = text.substring(0, markers.first().start).trim()
        if (lead.isNotEmpty()) {
            senses.add(Sense(number = null, text = lead, startsNewGroup = false, isSynonymBlock = false))
        }
        for (i in markers.indices) {
            val marker = markers[i]
            val end = if (i + 1 < markers.size) markers[i + 1].start else text.length
            val body = text.substring(marker.contentStart, end).trim()
            if (body.isEmpty()) continue
            // A sense numbered 1 that isn't the first sense in the entry means a
            // new part-of-speech group just started (the only signal this data
            // gives for that boundary at all).
            val newGroup = marker.number == 1 && senses.any { !it.isSynonymBlock }
            senses.add(Sense(marker.number, body, newGroup, isSynonymBlock = marker.number == null))
        }
        return senses
    }
}
