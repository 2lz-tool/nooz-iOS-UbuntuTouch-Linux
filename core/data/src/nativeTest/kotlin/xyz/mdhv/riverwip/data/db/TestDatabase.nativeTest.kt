package xyz.mdhv.riverwip.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random

actual fun testDatabaseBuilder(path: String): RoomDatabase.Builder<RiverDatabase> =
    Room.databaseBuilder<RiverDatabase>(name = path)

actual fun freshDatabasePath(): Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "river-test-${Random.nextLong().toString(16)}.db"

actual val testFileSystem: FileSystem = FileSystem.SYSTEM
