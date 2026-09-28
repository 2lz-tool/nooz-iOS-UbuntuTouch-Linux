package xyz.mdhv.riverwip.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import xyz.mdhv.riverwip.data.db.RiverDatabase

/**
 * The Android [DataPlatform]. Deliberately changes nothing a shipped install can
 * see: the same `river.db`, the same `datastore/*.preferences_pb` files, the same
 * framework SQLite under Room.
 */
class AndroidDataPlatform(context: Context) : DataPlatform {
    private val app = context.applicationContext

    override val fileSystem: FileSystem = FileSystem.SYSTEM
    override val filesDir: Path = app.filesDir.absolutePath.toPath()
    override val cacheDir: Path = app.cacheDir.absolutePath.toPath()
    override val stores: PreferenceStores by lazy { PreferenceStores(filesDir, fileSystem) }

    override fun readAsset(name: String): String? =
        runCatching { app.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()

    override fun databaseBuilder(): RoomDatabase.Builder<RiverDatabase> =
        Room.databaseBuilder(app, RiverDatabase::class.java, RiverDatabase.FILE_NAME)

    override val roomDriver: SQLiteDriver? = null
    override val sqliteDriver: SQLiteDriver = AndroidSQLiteDriver()

    override fun usableSpaceBytes(dir: Path): Long = dir.toFile().usableSpace
}
