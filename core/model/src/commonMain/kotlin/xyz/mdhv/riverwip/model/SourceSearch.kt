package xyz.mdhv.riverwip.model

/**
 * Matching and ranking for the sources picker (owner's ask, 2026-08: a search
 * "that matches the size of the list of resources we would now have").
 *
 * The picker's filter was `def.title.lowercase().contains(q)` — a single
 * substring over one field. That was defensible against twenty starters and is
 * not against a catalogue heading for two hundred, for three reasons a reader
 * meets immediately:
 *
 *  - **Word order.** "world bbc" found nothing, though "BBC World" is right
 *    there. Terms are matched independently now.
 *  - **One field.** Typing a domain the reader knows ("theguardian.com"), or a
 *    region ("india"), or a language they want ("Telugu"), searched none of
 *    them. Title, URL, homepage and region are all searched now — which is what
 *    makes D35's language-in-the-title convention actually reachable.
 *  - **No ranking.** Every hit was equal, so an exact title match could sit
 *    below an incidental URL match. Results are ordered by how well they match.
 *
 * Pure and unit-tested, like the rest of `:core:model`.
 */
object SourceSearch {

    /**
     * Match strength, best first. Ordinals are the sort key, so the order these
     * are declared in *is* the ranking.
     */
    private enum class Rank {
        TITLE_EXACT,
        TITLE_PREFIX,
        TITLE_WORD_PREFIX,
        TITLE_SUBSTRING,
        OTHER_FIELD,
    }

    /**
     * Everything about a service worth searching, lowercased once.
     *
     * The URL is included deliberately: readers know publishers by domain at
     * least as often as by masthead, and "npr.org" should find NPR.
     */
    private fun haystacks(def: ServiceDef): List<String> = listOfNotNull(
        def.title,
        def.url,
        def.homepage,
        def.region,
        def.notes,
    ).map { it.lowercase() }

    /**
     * Whether [def] matches every term in [query].
     *
     * Terms are ANDed and each may match any field — so "guardian world" finds
     * The Guardian's world feed, and "india tv9" finds the TV9 feeds tagged to
     * India even though no single field contains both words.
     */
    fun matches(def: ServiceDef, query: String): Boolean {
        val terms = ArticleSearch.terms(query)
        if (terms.isEmpty()) return true
        val fields = haystacks(def)
        return terms.all { term -> fields.any { it.contains(term) } }
    }

    /**
     * [defs] filtered to those matching [query], best match first.
     *
     * Ties keep their incoming order: the catalogue is already arranged
     * deliberately (region groupings, global outlets before regional ones), and
     * a search should not silently reshuffle what it did not rank.
     */
    fun <T> rank(items: List<T>, query: String, def: (T) -> ServiceDef): List<T> {
        val terms = ArticleSearch.terms(query)
        if (terms.isEmpty()) return items
        return items
            .mapIndexedNotNull { index, item ->
                val d = def(item)
                val fields = haystacks(d)
                if (!terms.all { term -> fields.any { it.contains(term) } }) return@mapIndexedNotNull null
                Triple(item, rankOf(d, terms), index)
            }
            .sortedWith(compareBy({ it.second.ordinal }, { it.third }))
            .map { it.first }
    }

    private fun rankOf(def: ServiceDef, terms: List<String>): Rank {
        val title = def.title.lowercase()
        val joined = terms.joinToString(" ")
        val titleWords = title.split(' ', ':', '-', '(', ')', ',', '.', '/').filter { it.isNotEmpty() }
        return when {
            title == joined -> Rank.TITLE_EXACT
            title.startsWith(joined) -> Rank.TITLE_PREFIX
            terms.all { term -> titleWords.any { it.startsWith(term) } } -> Rank.TITLE_WORD_PREFIX
            terms.all { term -> title.contains(term) } -> Rank.TITLE_SUBSTRING
            else -> Rank.OTHER_FIELD
        }
    }
}
