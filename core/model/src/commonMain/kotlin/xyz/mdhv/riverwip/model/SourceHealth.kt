package xyz.mdhv.riverwip.model

/**
 * Local source-health monitor (brief §P6). Every field here is derived from data
 * the fetch loop already records per source ([SourceEntity.lastFetchAt]/
 * `lastError`/`consecutiveFailures` in `:core:data`, populated since P2) — this
 * is a read model over that history, nothing new is collected. It is shown to
 * the user and kept local; nothing about it is ever transmitted anywhere (the
 * brief's own CI-side sentry, not the device, is what watches provider health
 * externally — see the `catalogue-sentry` GitHub Action).
 */
enum class HealthStatus { OK, STALE, RATE_LIMITED, FAILING, UNKNOWN }

data class SourceHealth(
    val sourceId: String,
    val lastFetchAt: Long?,
    val lastError: String?,
    val consecutiveFailures: Int,
    val status: HealthStatus,
)

object SourceHealthClassifier {
    /** No fixed fetch cadence is mandated (brief: user-set cadence) so this is a generous, cadence-agnostic default. */
    const val STALE_AFTER_MILLIS: Long = 48L * 60 * 60 * 1000

    fun classify(lastFetchAt: Long?, lastError: String?, consecutiveFailures: Int, now: Long): HealthStatus {
        if (lastFetchAt == null) return HealthStatus.UNKNOWN
        if (lastError != null) return if (isRateLimited(lastError)) HealthStatus.RATE_LIMITED else HealthStatus.FAILING
        return if (now - lastFetchAt > STALE_AFTER_MILLIS) HealthStatus.STALE else HealthStatus.OK
    }

    fun isRateLimited(error: String): Boolean =
        error.contains("429") ||
            error.contains("Too Many Requests", ignoreCase = true) ||
            error.contains("Retry-After", ignoreCase = true)
}
