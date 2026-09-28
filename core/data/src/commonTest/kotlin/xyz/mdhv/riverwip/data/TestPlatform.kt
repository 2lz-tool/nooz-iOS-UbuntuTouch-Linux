package xyz.mdhv.riverwip.data

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import okio.FileSystem
import okio.Path
import xyz.mdhv.riverwip.data.db.RiverDatabase
import xyz.mdhv.riverwip.data.db.freshDatabasePath
import xyz.mdhv.riverwip.data.db.testDatabaseBuilder
import xyz.mdhv.riverwip.data.db.testFileSystem

/** A [DataPlatform] on a throw-away directory of the real file system, with the bundled SQLite. */
class TestPlatform(
    val root: Path = freshDatabasePath().also { testFileSystem.createDirectories(it) },
) : PathsDataPlatform(root / "files", root / "cache") {
    override val fileSystem: FileSystem = testFileSystem
    private val bundled = BundledSQLiteDriver()
    override val roomDriver: SQLiteDriver = bundled
    override val sqliteDriver: SQLiteDriver = bundled

    override fun readAsset(name: String): String? = null
    override fun databaseBuilder(): RoomDatabase.Builder<RiverDatabase> = testDatabaseBuilder((root / "river.db").toString())
    override fun usableSpaceBytes(dir: Path): Long = -1

    fun cleanUp() = fileSystem.deleteRecursively(root, mustExist = false)
}
