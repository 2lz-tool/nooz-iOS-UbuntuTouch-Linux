package xyz.mdhv.riverwip.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/**
 * Schedules the periodic fetch (brief §P2: "WorkManager fetch on user-set
 * cadence"), respecting modern foreground-service limits by using ordinary
 * (non-expedited) periodic work. WorkManager's minimum periodic interval is 15
 * minutes; a shorter request is clamped rather than silently ignored.
 */
object FetchScheduler {

    private fun networkConstraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** (Re)schedule the periodic fetch at [intervalMinutes] (user-set cadence). */
    fun schedule(context: Context, intervalMinutes: Long = 60L) {
        val interval = intervalMinutes.coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<FetchWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FetchWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** User-triggered "refresh now" (e.g. pull-to-refresh), independent of the periodic schedule. */
    fun triggerNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<FetchWorker>().setConstraints(networkConstraints()).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(FetchWorker.UNIQUE_WORK_NAME)
    }
}
