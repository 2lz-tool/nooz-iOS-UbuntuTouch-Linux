package xyz.mdhv.riverwip.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.FileSystem
import okio.Path
import xyz.mdhv.riverwip.data.db.RiverDatabase

/**
 * [DataPlatform] for the desktop JVM (Linux): plain directories (XDG-style paths are
 * chosen by the app) and the bundled SQLite build, so behaviour does not depend on
 * whichever libsqlite the distribution happens to ship.
 */
class DesktopDataPlatform(filesDir: Path, cacheDir: Path) : PathsDataPlatform(filesDir, cacheDir) {
    override val fileSystem: FileSystem = FileSystem.SYSTEM

    override fun readAsset(name: String): String? =
        DesktopDataPlatform::class.java.classLoader?.getResourceAsStream(name)?.bufferedReader()?.use { it.readText() }

    override fun databaseBuilder(): RoomDatabase.Builder<RiverDatabase> {
        fileSystem.createDirectories(filesDir)
        return Room.databaseBuilder<RiverDatabase>(name = (filesDir / RiverDatabase.FILE_NAME).toString())
    }

    override val roomDriver: SQLiteDriver = BundledSQLiteDriver()
    override val sqliteDriver: SQLiteDriver = BundledSQLiteDriver()

    override fun usableSpaceBytes(dir: Path): Long = dir.toFile().usableSpace
}
