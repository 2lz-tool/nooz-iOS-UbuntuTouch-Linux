package xyz.mdhv.riverwip.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.File

actual fun testDatabaseBuilder(path: String): RoomDatabase.Builder<RiverDatabase> =
    Room.databaseBuilder<RiverDatabase>(name = path)

actual fun freshDatabasePath(): Path = File.createTempFile("river-test", ".db").also { it.delete() }.absolutePath.let { okio.Path.Companion.run { it.toPath() } }

actual val testFileSystem: FileSystem = FileSystem.SYSTEM
