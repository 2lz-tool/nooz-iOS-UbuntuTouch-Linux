package xyz.mdhv.riverwip.model

/**
 * Deduplication (brief §2): collapse the same story arriving from multiple
 * sources. Two-stage, and legible — the caller can see *why* two items merged.
 *
 *  1. Exact: identical [Item.canonicalUrl] → same story.
 *  2. Near: title SimHash within [Simhash.NEAR_DUP_THRESHOLD] bits → same story
 *     (syndication, wire copy, minor rewrites).
 *
 * Pure. The first item seen for a cluster is the representative; later duplicates
 * point at it. Order-stable so results are testable.
 */
object Dedup {

    data class Cluster(val representative: Item, val duplicates: List<Item>) {
        val all: List<Item> get() = listOf(representative) + duplicates
        val size: Int get() = 1 + duplicates.size
    }

    /**
     * Group [items] into clusters. Within a batch this is O(n·k) where k is the
     * number of distinct clusters (near-dup check compares against existing
     * representatives only) — fine for a fetch cycle's worth of items.
     */
    fun cluster(items: List<Item>, threshold: Int = Simhash.NEAR_DUP_THRESHOLD): List<Cluster> {
        val byUrl = HashMap<String, Int>() // canonicalUrl -> cluster index
        val reps = ArrayList<Item>()
        val dups = ArrayList<ArrayList<Item>>()

        for (item in items) {
            val existingByUrl = byUrl[item.canonicalUrl]
            if (existingByUrl != null) {
                dups[existingByUrl].add(item)
                continue
            }
            // Near-duplicate against existing representatives.
            var matched = -1
            if (item.simhash != 0L) {
                for (i in reps.indices) {
                    // Threshold scaled to how much evidence these two titles
                    // actually carry (Simhash.thresholdFor). A short headline
                    // gives simhash little to work with, and a fixed 8 bits was
                    // collapsing genuinely different short stories — measurably
                    // so in scripts that pack more meaning into fewer characters.
                    val budget = Simhash.thresholdFor(item.title, reps[i].title, threshold)
                    if (Simhash.isNearDuplicate(item.simhash, reps[i].simhash, budget)) {
                        matched = i
                        break
                    }
                }
            }
            if (matched >= 0) {
                dups[matched].add(item)
                byUrl[item.canonicalUrl] = matched
            } else {
                val idx = reps.size
                reps.add(item)
                dups.add(ArrayList())
                byUrl[item.canonicalUrl] = idx
            }
        }
        return reps.indices.map { Cluster(reps[it], dups[it]) }
    }

    /** Convenience: the deduplicated representatives only, order-stable. */
    fun deduplicate(items: List<Item>, threshold: Int = Simhash.NEAR_DUP_THRESHOLD): List<Item> =
        cluster(items, threshold).map { it.representative }
}
