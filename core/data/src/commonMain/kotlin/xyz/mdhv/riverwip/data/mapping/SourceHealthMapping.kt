package xyz.mdhv.riverwip.data.mapping

import xyz.mdhv.riverwip.data.db.SourceEntity
import xyz.mdhv.riverwip.model.SourceHealth
import xyz.mdhv.riverwip.model.SourceHealthClassifier

fun SourceEntity.toHealth(now: Long): SourceHealth = SourceHealth(
    sourceId = id,
    lastFetchAt = lastFetchAt,
    lastError = lastError,
    consecutiveFailures = consecutiveFailures,
    status = SourceHealthClassifier.classify(lastFetchAt, lastError, consecutiveFailures, now),
)
