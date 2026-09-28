package xyz.mdhv.riverwip.model

/**
 * Groups headlines that appear to cover the **same story** across different
 * sources, so their framings can be set side by side (owner's contrast idea,
 * phase 3 — the "invades" vs "cross into" comparison). This is the pure,
 * on-device half: clustering by shared significant words, no model needed. The
 * *automatic* highlighting of which words differ in loaded-ness is the lens's
 * job and stays honestly stubbed until a real provider is wired — but a reader
 * can already see the difference themselves once the headlines sit together.
 *
 * Deliberately conservative: a cluster forms only when two headlines from
 * **different** sources share at least [DEFAULT_MIN_SHARED] significant words,
 * and only clusters that span two or more sources are returned — a single
 * outlet repeating itself isn't a framing contrast. The heuristic will miss
 * some pairings and occasionally over-merge; it's a lens for noticing, not a
 * claim of ground truth.
 */
object StoryClustering {

    const val DEFAULT_MIN_SHARED = 2

    /** Dropped before matching: function words and newswire filler that co-occur across unrelated stories. */
    private val STOPWORDS = setOf(
        "said", "says", "amid", "over", "after", "with", "that", "this", "from",
        "into", "live", "news", "update", "updates", "could", "would", "will",
        "have", "has", "been", "were", "was", "are", "their", "they", "them",
        "more", "than", "then", "your", "you", "its", "his", "her", "our",
        "out", "off", "how", "why", "what", "when", "where", "who", "the",
        "and", "for", "but", "not", "new", "amid", "about", "which", "while",
        "still", "back", "day", "week", "year", "years", "top", "story",
        "stories", "report", "reports", "reveals", "amid",
    )

    /** One headline to be clustered. */
    data class Doc(val id: String, val title: String, val sourceId: String)

    /**
     * A set of headlines judged to be the same story. [keywords] are the words
     * the members most share (a rough label); [members] preserves input order.
     */
    data class Cluster(val keywords: List<String>, val members: List<Doc>) {
        val sourceCount: Int get() = members.map { it.sourceId }.distinct().size
    }

    /** Significant lowercase words in a title: alphanumeric runs of length ≥ 4, minus stopwords. */
    fun keywordsOf(title: String): Set<String> =
        title.lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length >= 4 && it !in STOPWORDS }
            .toSet()

    fun cluster(docs: List<Doc>, minShared: Int = DEFAULT_MIN_SHARED): List<Cluster> {
        if (docs.size < 2) return emptyList()
        val kw = docs.map { keywordsOf(it.title) }
        val parent = IntArray(docs.size) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cur = x
            while (parent[cur] != root) {
                val next = parent[cur]
                parent[cur] = root
                cur = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        for (i in docs.indices) {
            for (j in i + 1 until docs.size) {
                if (docs[i].sourceId == docs[j].sourceId) continue
                val shared = kw[i].count { it in kw[j] }
                if (shared >= minShared) union(i, j)
            }
        }

        val groups = docs.indices.groupBy { find(it) }
        return groups.values.mapNotNull { idxs ->
            val members = idxs.map { docs[it] }
            if (members.map { it.sourceId }.distinct().size < 2) return@mapNotNull null
            val topKeywords = idxs.flatMap { kw[it] }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(4)
                .map { it.key }
            Cluster(topKeywords, members)
        }.sortedWith(compareByDescending<Cluster> { it.sourceCount }.thenByDescending { it.members.size })
    }
}
