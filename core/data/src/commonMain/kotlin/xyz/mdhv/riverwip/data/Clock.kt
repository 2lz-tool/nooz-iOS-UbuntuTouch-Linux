package xyz.mdhv.riverwip.data

import kotlin.time.Clock

/** Wall-clock epoch milliseconds; `nowMillis()` is JVM-only. */
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
