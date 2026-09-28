package xyz.mdhv.riverwip.model

/**
 * The pure ingest transform (brief §P2): a fetched feed body + its source →
 * classified, dedup-ready [Item]s. Everything the pipeline decides here is
 * legible — canonical URL, simhash, and topic evidence are all attached and
 * inspectable. The Android layer only supplies IO (fetch + persist).
 */
object Ingest {

    /** Build a single [Item] from a parsed feed entry. */
    fun buildItem(source: Source, parsed: FeedParser.ParsedItem, fetchedAt: Long): Item {
        val canonical = CanonicalUrl.canonicalize(parsed.link.ifBlank { parsed.title })
        val topics = Classifier.classify(parsed.title, parsed.summary, parsed.categories)
        return Item(
            id = Ids.itemId(canonical),
            sourceId = source.id,
            canonicalUrl = canonical,
            title = parsed.title,
            author = parsed.author,
            publishedAt = parsed.publishedAtMillis ?: fetchedAt,
            fetchedAt = fetchedAt,
            summary = parsed.summary,
            fullTextCached = false,
            topics = topics,
            simhash = Simhash.of(parsed.title),
            imageUrl = parsed.imageUrl,
            declaredNsfw = parsed.declaredNsfw,
        )
    }

    /**
     * Full transform for one fetch: parse → build items → dedup within the batch.
     * Returns the deduplicated representatives, newest first by publish time.
     */
    fun ingest(source: Source, body: String, contentType: String?, fetchedAt: Long): List<Item> {
        val feed = FeedParser.parse(body, contentType)
        val items = feed.items.map { buildItem(source, it, fetchedAt) }
        return Dedup.deduplicate(items).sortedByDescending { it.publishedAt }
    }
}
