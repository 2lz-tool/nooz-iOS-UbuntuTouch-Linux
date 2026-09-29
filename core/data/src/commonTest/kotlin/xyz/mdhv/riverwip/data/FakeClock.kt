package xyz.mdhv.riverwip.data

import kotlin.time.Clock
import kotlin.time.Instant
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/** A clock that advances 10 ms per read, so files written in sequence get distinct, ordered modification times. */
class TickingClock : Clock {
    private var t = 1_000_000L
    override fun now(): Instant = Instant.fromEpochMilliseconds(t).also { t += 10 }
}

fun newFakeFileSystem() = FakeFileSystem(clock = TickingClock())

val TEST_DIR = "/cache".toPath()
