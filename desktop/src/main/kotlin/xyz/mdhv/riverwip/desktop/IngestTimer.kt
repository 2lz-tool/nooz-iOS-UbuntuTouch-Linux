package xyz.mdhv.riverwip.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.IngestCycle

/**
 * The desktop stand-in for WorkManager: fetch the reader's sources shortly after launch and then
 * hourly, for as long as the app is open. Failures are swallowed per cycle -- one bad network
 * moment must not end the loop.
 */
class IngestTimer(
    private val scope: CoroutineScope,
    private val cycle: IngestCycle,
    private val initialDelayMillis: Long = 2_000,
    private val intervalMillis: Long = 60L * 60 * 1000,
) {
    fun start(): Job = scope.launch {
        delay(initialDelayMillis)
        while (isActive) {
            try {
                cycle.run()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Try again next cycle.
            }
            delay(intervalMillis)
        }
    }
}
