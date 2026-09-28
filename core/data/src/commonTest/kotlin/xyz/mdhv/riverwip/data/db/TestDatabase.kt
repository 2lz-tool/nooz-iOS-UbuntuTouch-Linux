package xyz.mdhv.riverwip.data.db

import androidx.room.RoomDatabase
import okio.Path

/** A Room builder for a database file at [path]; the platform decides how (no common API for it). */
expect fun testDatabaseBuilder(path: String): RoomDatabase.Builder<RiverDatabase>

/** A fresh, unique database path in the platform's temp directory. */
expect fun freshDatabasePath(): Path

/** The real file system: `FileSystem.SYSTEM` is not nameable from common code. */
expect val testFileSystem: okio.FileSystem
