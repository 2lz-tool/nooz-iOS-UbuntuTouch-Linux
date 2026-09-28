package xyz.mdhv.riverwip.data.db

import xyz.mdhv.riverwip.data.IoDispatcher
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

/**
 * The app's Room database. Manual DI: constructed once in the composition root
 * and handed to repositories.
 *
 * Schemas are exported to `core/data/schemas/` and checked in as of v4 (D37).
 * They were not before, on the reasoning that the schema was "v1 and unshipped"
 * — which stopped being true at versionCode 2. Exported schemas are what let
 * `RiverDatabaseMigrationTest` open a real v3 database, run the migration, and
 * assert the result, instead of asserting a migration is correct because it
 * looks correct.
 */
@Database(
    entities = [
        SourceEntity::class,
        ItemEntity::class,
        ReadEventEntity::class,
        WeeklyAggregateEntity::class,
        ClippingEntity::class,
        ArticleTextEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@ConstructedBy(RiverDatabaseConstructor::class)
abstract class RiverDatabase : RoomDatabase() {
    abstract fun sourceDao(): SourceDao
    abstract fun itemDao(): ItemDao
    abstract fun readEventDao(): ReadEventDao
    abstract fun weeklyAggregateDao(): WeeklyAggregateDao
    abstract fun clippingDao(): ClippingDao
    abstract fun articleTextDao(): ArticleTextDao

    companion object {
        /**
         * v3 → v4: adds the `article_text` FTS4 index behind the Stand's
         * search (D37).
         *
         * This is the database's first real migration, and it exists because
         * the alternative had become unacceptable: `fallbackToDestructiveMigration`
         * alone would have answered a schema bump by deleting every shipped
         * reader's clippings, read events and weekly aggregates — which is to
         * say the entire history the Loom draws, and the one part of this app
         * the brief calls the reader's own.
         *
         * The DDL is copied verbatim from Room's own exported `4.json`, so the
         * table this creates is byte-identical to the one Room would create on
         * a fresh install. Room compares that statement on open; a
         * hand-written near-miss passes review and then throws
         * IllegalStateException on a real device.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `article_text` " +
                        "USING FTS4(`itemId` TEXT NOT NULL, `body` TEXT NOT NULL, tokenize=unicode61)",
                )
            }
        }

        /**
         * Finish a platform-supplied builder (which knows where `river.db` lives) with
         * the migration, the driver (null = the platform's own) and the fallback.
         */
        fun build(builder: RoomDatabase.Builder<RiverDatabase>, driver: SQLiteDriver?): RiverDatabase =
            builder
                .apply { if (driver != null) setDriver(driver) }
                .setQueryCoroutineContext(IoDispatcher)
                .addMigrations(MIGRATION_3_4)
                // Kept only as the last resort it was always meant to be: with
                // the migration above, a shipped v3 reader upgrades with their
                // data intact, and this now catches nothing but a pre-release
                // database old enough to have no path forward at all.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        const val FILE_NAME = "river.db"
    }
}

/** Filled in by Room's compiler for every target. */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object RiverDatabaseConstructor : RoomDatabaseConstructor<RiverDatabase> {
    override fun initialize(): RiverDatabase
}
