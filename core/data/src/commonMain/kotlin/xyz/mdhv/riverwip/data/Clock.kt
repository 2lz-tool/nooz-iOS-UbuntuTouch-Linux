package xyz.mdhv.riverwip.data

import kotlinx.datetime.Clock

/** Wall-clock epoch milliseconds; `nowMillis()` is JVM-only. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
