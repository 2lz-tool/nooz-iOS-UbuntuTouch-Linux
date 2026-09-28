package xyz.mdhv.riverwip.model

/**
 * Legible-by-construction classifier (brief §2). Two rule families, each emitting
 * [TopicEvidence] that names the rule and the exact terms it matched:
 *   - `feedcat:<topic>` — a feed-declared category mapped to the taxonomy.
 *   - `lexicon:<topic>` — keyword/phrase terms found in title + summary.
 * If nothing fires, a single `fallback` evidence assigns [Topic.OTHER].
 *
 * No opaque classifier in v1 — every number the river shows can be traced back
 * through this to a rule and its terms (brief §3).
 */
object Classifier {

    fun classify(
        title: String,
        summary: String? = null,
        feedCategories: List<String> = emptyList(),
    ): List<TopicEvidence> {
        val evidenceByTopic = LinkedHashMap<Topic, MutableList<TopicEvidence>>()

        // (a) Feed-declared categories → taxonomy.
        for (raw in feedCategories) {
            val cat = raw.trim().lowercase()
            if (cat.isEmpty()) continue
            val topic = TopicLexicon.categoryMap.entries
                .firstOrNull { (key, _) -> cat == key || cat.contains(key) }
                ?.value ?: continue
            evidenceByTopic.getOrPut(topic) { mutableListOf() }
                .add(TopicEvidence(topic, ruleId = "feedcat:${topic.key}", matchedTerms = listOf(raw.trim())))
        }

        // (b) Lexicon rules over title + summary.
        val haystack = buildString {
            append(title)
            if (!summary.isNullOrBlank()) append(' ').append(summary)
        }
        for ((topic, matchers) in TopicLexicon.matchers) {
            val hits = matchers.mapNotNull { (term, regex) -> if (regex.containsMatchIn(haystack)) term else null }
            if (hits.isNotEmpty()) {
                evidenceByTopic.getOrPut(topic) { mutableListOf() }
                    .add(TopicEvidence(topic, ruleId = "lexicon:${topic.key}", matchedTerms = hits))
            }
        }

        if (evidenceByTopic.isEmpty()) {
            return listOf(TopicEvidence(Topic.OTHER, ruleId = "fallback", matchedTerms = emptyList()))
        }
        // Flatten, preserving discovery order; all evidence is retained.
        return evidenceByTopic.values.flatten()
    }

    /**
     * The single dominant topic used for stream/read counts. Chosen by total
     * matched-term weight (feed-category hits count as one strong term), ties
     * broken by taxonomy order for determinism. This is a *count* decision only;
     * the full evidence list is always kept for inspection.
     */
    fun dominantTopic(evidence: List<TopicEvidence>): Topic {
        if (evidence.isEmpty()) return Topic.OTHER
        val score = LinkedHashMap<Topic, Int>()
        for (e in evidence) {
            val w = when {
                e.ruleId.startsWith("feedcat:") -> 2 + e.matchedTerms.size
                e.ruleId == "fallback" -> 0
                else -> e.matchedTerms.size
            }
            score[e.topic] = (score[e.topic] ?: 0) + w
        }
        val maxScore = score.values.maxOrNull() ?: return Topic.OTHER
        // Tie-break by taxonomy (enum) order for determinism.
        return Topic.entries.firstOrNull { score[it] == maxScore && maxScore > 0 }
            ?: Topic.OTHER
    }

    /** Convenience for ingest: attach evidence to a parsed item. */
    fun topicsFor(title: String, summary: String?, feedCategories: List<String>): List<TopicEvidence> =
        classify(title, summary, feedCategories)
}
