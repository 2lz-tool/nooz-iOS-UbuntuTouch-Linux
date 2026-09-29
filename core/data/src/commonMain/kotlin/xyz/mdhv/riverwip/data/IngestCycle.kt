package xyz.mdhv.riverwip.data

import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository

/**
 * One scheduled ingest (brief §P2): fetch every enabled source, ingest, roll the result into weekly
 * aggregates, then prune Item rows past retention and the search-index rows that belonged to them.
 * Per-source failures are isolated inside [ItemRepository.fetchAndIngestAllEnabled] -- one source
 * going down never fails the whole run. Android's WorkManager worker and the desktop timer both run this.
 */
class IngestCycle(
    private val itemRepository: ItemRepository,
    private val weeklyAggregateRepository: WeeklyAggregateRepository,
    private val articleRepository: ArticleRepository,
) {
    suspend fun run() {
        itemRepository.fetchAndIngestAllEnabled()
        weeklyAggregateRepository.recompute()
        itemRepository.pruneOlderThan()
        // Retention just removed items; the search index must follow them out or it keeps prose for
        // stories that can no longer be opened.
        articleRepository.pruneIndexOrphans()
    }
}
