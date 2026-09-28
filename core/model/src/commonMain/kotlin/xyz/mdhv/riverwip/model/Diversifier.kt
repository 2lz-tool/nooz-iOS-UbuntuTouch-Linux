package xyz.mdhv.riverwip.model

/**
 * Reorders items for maximum source spread (owner's ask, 2026-07: "mix the
 * articles for maximum spread/coverage"). A round-robin interleave by source
 * — not a random shuffle — so consecutive items rarely share a source unless
 * one source genuinely dominates the supply. Pure and deterministic: the same
 * input always spreads the same way, and each source's own relative order
 * (already chronological on the way in) is preserved within itself.
 */
object Diversifier {
    fun spread(items: List<Item>): List<Item> {
        if (items.size <= 1) return items
        val bySource = LinkedHashMap<String, ArrayDeque<Item>>()
        for (item in items) {
            bySource.getOrPut(item.sourceId) { ArrayDeque() }.add(item)
        }
        val sourceOrder = bySource.keys.toList()
        val out = ArrayList<Item>(items.size)
        var remaining = items.size
        var i = 0
        while (remaining > 0) {
            val queue = bySource.getValue(sourceOrder[i % sourceOrder.size])
            if (queue.isNotEmpty()) {
                out.add(queue.removeFirst())
                remaining--
            }
            i++
        }
        return out
    }
}
