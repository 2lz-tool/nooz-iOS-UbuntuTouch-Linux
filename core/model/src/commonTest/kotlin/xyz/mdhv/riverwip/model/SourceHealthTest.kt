package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceHealthTest {

    private val now = 1_800_000_000_000L

    @Test fun neverFetchedIsUnknown() {
        assertEquals(HealthStatus.UNKNOWN, SourceHealthClassifier.classify(null, null, 0, now))
    }

    @Test fun recentSuccessWithNoErrorIsOk() {
        val recent = now - 60_000L
        assertEquals(HealthStatus.OK, SourceHealthClassifier.classify(recent, null, 0, now))
    }

    @Test fun successLongAgoWithNoErrorIsStale() {
        val longAgo = now - SourceHealthClassifier.STALE_AFTER_MILLIS - 1
        assertEquals(HealthStatus.STALE, SourceHealthClassifier.classify(longAgo, null, 0, now))
    }

    @Test fun justUnderTheStalenessThresholdIsStillOk() {
        val almost = now - SourceHealthClassifier.STALE_AFTER_MILLIS + 1
        assertEquals(HealthStatus.OK, SourceHealthClassifier.classify(almost, null, 0, now))
    }

    @Test fun http429ErrorIsRateLimited() {
        assertEquals(HealthStatus.RATE_LIMITED, SourceHealthClassifier.classify(now, "HTTP 429", 1, now))
    }

    @Test fun tooManyRequestsTextIsRateLimited() {
        assertEquals(HealthStatus.RATE_LIMITED, SourceHealthClassifier.classify(now, "Too Many Requests", 2, now))
    }

    @Test fun otherErrorsAreFailingRegardlessOfStreakLength() {
        assertEquals(HealthStatus.FAILING, SourceHealthClassifier.classify(now, "HTTP 500", 1, now))
        assertEquals(HealthStatus.FAILING, SourceHealthClassifier.classify(now, "timeout", 5, now))
    }

    @Test fun rateLimitTakesPriorityOverStaleness() {
        val longAgo = now - SourceHealthClassifier.STALE_AFTER_MILLIS - 1
        assertEquals(HealthStatus.RATE_LIMITED, SourceHealthClassifier.classify(longAgo, "HTTP 429", 3, now))
    }
}
