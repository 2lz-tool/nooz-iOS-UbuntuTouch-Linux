package xyz.mdhv.riverwip.bridge

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import xyz.mdhv.riverwip.data.PathsDataPlatform
import xyz.mdhv.riverwip.data.db.RiverDatabase

/**
 * [xyz.mdhv.riverwip.data.DataPlatform] for a native Linux host. The host says where its data and
 * cache directories are (on Ubuntu Touch, the app's confined `~/.local/share/<app>` and
 * `~/.cache/<app>`) and where the packaged read-only assets were installed.
 */
class LinuxDataPlatform(filesDir: String, cacheDir: String, private val assetsDir: String) :
    PathsDataPlatform(filesDir, cacheDir) {
    override val fileSystem: FileSystem = FileSystem.SYSTEM

    override fun readAsset(name: String): String? = runCatching {
        val path = assetsDir.toPath() / name
        if (fileSystem.exists(path)) fileSystem.read(path) { readUtf8() } else null
    }.getOrNull()

    override fun databaseBuilder(): RoomDatabase.Builder<RiverDatabase> {
        fileSystem.createDirectories(filesDir)
        return Room.databaseBuilder<RiverDatabase>(name = (filesDir / RiverDatabase.FILE_NAME).toString())
    }

    override val roomDriver: SQLiteDriver = BundledSQLiteDriver()
    override val sqliteDriver: SQLiteDriver = BundledSQLiteDriver()

    override fun usableSpaceBytes(dir: Path): Long = -1
}
