package xyz.mdhv.riverwip.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import xyz.mdhv.riverwip.data.db.RiverDatabase

/**
 * Everything the data layer needs from the platform it runs on, in one place.
 *
 * `:core:data` itself is platform-free: it never asks for an Android `Context`, a
 * home directory or a bundled resource directly. An app hands it a [DataPlatform]
 * -- `AndroidDataPlatform(context)` on Android, [PathsDataPlatform] subclasses on
 * desktop Linux, iOS and Ubuntu Touch -- and the repositories are wired from that.
 */
interface DataPlatform {
    /** The real file system (`FileSystem.SYSTEM`); an interface member because common code can't name it on every target. */
    val fileSystem: FileSystem

    /** Durable per-user files (downloaded dictionaries, translation packs, models). */
    val filesDir: Path

    /** Evictable files (the full-text cache). */
    val cacheDir: Path

    /** Preference stores, one per concern, at the paths the Android app has always used. */
    val stores: PreferenceStores

    /** Read a bundled text asset (`common_words.txt`, `ai_catalogue_models.json`), or null if missing. */
    fun readAsset(name: String): String?

    /** A Room builder pointing at `river.db` in this platform's database location. */
    fun databaseBuilder(): RoomDatabase.Builder<RiverDatabase>

    /** Driver Room should use, or null for the platform's own (Android keeps its framework SQLite, so existing databases open exactly as before). */
    val roomDriver: SQLiteDriver?

    /** Driver for direct SQLite access (the translation packs). */
    val sqliteDriver: SQLiteDriver

    /** Free bytes where [dir] lives, or -1 if the platform can't say. */
    fun usableSpaceBytes(dir: Path): Long
}

/**
 * The app's preference stores. Created eagerly and once, because a DataStore file
 * must never have two live instances; nothing touches the disk until a store is read.
 * The `datastore/<name>.preferences_pb` layout is exactly what Android's
 * `preferencesDataStore(name)` delegate used, so an existing install keeps its settings.
 */
class PreferenceStores(private val filesDir: Path, private val fileSystem: FileSystem) {
    private fun open(name: String): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
        produceFile = {
            val dir = filesDir / "datastore"
            fileSystem.createDirectories(dir)
            dir / "$name.preferences_pb"
        },
    )

    val settings: DataStore<Preferences> = open("display_settings")
    val catalogue: DataStore<Preferences> = open("catalogue")
    val dictionary: DataStore<Preferences> = open("dictionary")
    val modelCatalogue: DataStore<Preferences> = open("model_catalogue")
    val todayInHistory: DataStore<Preferences> = open("today_in_history")
    val translation: DataStore<Preferences> = open("translation")
}

/** Convenience base for platforms that are just "a directory for files, a directory for cache". */
abstract class PathsDataPlatform(
    override val filesDir: Path,
    override val cacheDir: Path,
) : DataPlatform {
    constructor(filesDir: String, cacheDir: String) : this(filesDir.toPath(), cacheDir.toPath())

    override val stores: PreferenceStores by lazy { PreferenceStores(filesDir, fileSystem) }
}
