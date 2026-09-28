package xyz.mdhv.riverwip.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository

/**
 * Scheduled ingest (brief §P2): fetch every enabled source, ingest, roll the
 * result into weekly aggregates, then prune Item rows past retention and the
 * search-index rows that belonged to them. Per-source
 * failures are isolated inside [ItemRepository.fetchAndIngestAllEnabled] — one
 * source going down never fails the whole run.
 */
class FetchWorker(
    context: Context,
    params: WorkerParameters,
    private val itemRepository: ItemRepository,
    private val weeklyAggregateRepository: WeeklyAggregateRepository,
    private val articleRepository: ArticleRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        itemRepository.fetchAndIngestAllEnabled()
        weeklyAggregateRepository.recompute()
        itemRepository.pruneOlderThan()
        // Retention just removed items; the search index must follow them
        // out or it keeps prose for stories that can no longer be opened.
        articleRepository.pruneIndexOrphans()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "river-fetch"
    }
}
