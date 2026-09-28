package xyz.mdhv.riverwip.model

/**
 * Topic taxonomy v1. Fixed in v1 (brief §4).
 *
 * **The enum names are stable KEYS, not final display labels.** The human-facing
 * labels are RESERVED (brief §4/§9) — do not hardcode a final label anywhere.
 * [Topic.key] is the persisted/serialized key; [Topic.placeholderLabel] is a
 * working label surfaced in UI only until the RESERVED labels land.
 *
 * A need for user-editable taxonomy is a logged open question (STATE.md §10), not
 * a build item.
 */
enum class Topic(val key: String) {
    POLITICS("politics"),
    CONFLICT("conflict"),
    BUSINESS("business"),
    TECH("tech"),
    SCIENCE("science"),
    CLIMATE("climate"),
    HEALTH("health"),
    CULTURE("culture"),
    SPORT("sport"),
    OTHER("other");

    /**
     * Working label. RESERVED: the final labels belong to the owner. Until then
     * we surface a plain capitalization of the key so the UI is legible without
     * pre-empting the naming decision.
     */
    val placeholderLabel: String get() = key.replaceFirstChar { it.uppercase() }

    companion object {
        private val byKey = entries.associateBy(Topic::key)

        /** Resolve a persisted key back to a [Topic]; unknown keys fall to [OTHER]. */
        fun fromKey(key: String): Topic = byKey[key.lowercase()] ?: OTHER

        /** The catch-all bucket. Classification always terminates here if nothing fires. */
        val fallback: Topic = OTHER
    }
}
