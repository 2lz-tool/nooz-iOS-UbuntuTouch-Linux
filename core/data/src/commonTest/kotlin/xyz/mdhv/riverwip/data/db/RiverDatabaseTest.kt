package xyz.mdhv.riverwip.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Room on a real SQLite file, on every platform that builds this module.
 *
 * The migration test opens a genuine v3 database (its DDL is the checked-in
 * `schemas/.../3.json`) and asserts the reader's own data is still there after
 * the upgrade -- the thing `fallbackToDestructiveMigration` alone would have
 * deleted. The search tests check the FTS4 tokenizer, which no amount of
 * reading the DDL would catch.
 */
class RiverDatabaseTest {

    private val driver = BundledSQLiteDriver()

    private fun open(path: okio.Path): RiverDatabase =
        RiverDatabase.build(testDatabaseBuilder(path.toString()), driver)

    private fun withDb(block: suspend (RiverDatabase) -> Unit) = runTest {
        val path = freshDatabasePath()
        val db = open(path)
        try {
            block(db)
        } finally {
            db.close()
            FileSystem.SYSTEM.delete(path, mustExist = false)
        }
    }

    @Test fun migratingFromV3KeepsTheReadersOwnData() = runTest {
        val path = freshDatabasePath()
        try {
            createV3Database(path.toString())
            val db = open(path)
            try {
                val clippings = db.clippingDao().observeAll().first()
                assertEquals(listOf("Flash floods on the Nepal-Tibet border"), clippings.map { it.title }, "the clipping survived the migration")
                assertEquals(1, db.readEventDao().allOnce().size, "read events survived the migration")
                assertEquals(0, db.articleTextDao().count(), "the new index exists and starts empty")
            } finally {
                db.close()
            }
        } finally {
            FileSystem.SYSTEM.delete(path, mustExist = false)
        }
    }

    @Test fun ftsIndexFindsBothLatinAndIndicScriptsByPrefix() = withDb { db ->
        val dao = db.articleTextDao()
        dao.insert(ArticleTextEntity(itemId = "en", body = "Two dozen states filed a fresh lawsuit on Wednesday."))
        dao.insert(ArticleTextEntity(itemId = "te", body = "ఆంధ్ర ప్రదేశ్ లో భారీ వర్షాలు కురిశాయి"))
        dao.insert(ArticleTextEntity(itemId = "hi", body = "मोदी और पुतिन के बीच बैठक हुई"))

        assertEquals(listOf("en"), dao.search("lawsuit*", 10).map { it.itemId })
        assertEquals(listOf("en"), dao.search("laws*", 10).map { it.itemId })
        assertEquals(listOf("te"), dao.search("ఆంధ్ర*", 10).map { it.itemId })
        assertEquals(listOf("hi"), dao.search("पुतिन*", 10).map { it.itemId })
        assertEquals(listOf("en"), dao.search("dozen* lawsuit*", 10).map { it.itemId })
        assertTrue(dao.search("dozen* monsoon*", 10).isEmpty(), "a term the article lacks excludes it")
        // Not asserted here: how a literal "AND" behaves. Android's framework SQLite searches for the word, the bundled
        // SQLite (enhanced query syntax) treats it as an operator. ArticleSearch never emits it -- terms are joined by
        // whitespace, which means AND in both -- so the difference is invisible to the app.
    }

    @Test fun reindexingAnArticleDoesNotDuplicateIt() = withDb { db ->
        val dao = db.articleTextDao()
        dao.insert(ArticleTextEntity(itemId = "a", body = "first extraction mentions monsoon"))
        dao.deleteFor("a")
        dao.insert(ArticleTextEntity(itemId = "a", body = "second extraction mentions monsoon"))
        val hits = dao.search("monsoon*", 10)
        assertEquals(1, hits.size, "exactly one row for the article")
        assertTrue(hits.single().body.startsWith("second"), "and it is the newer body")
        assertEquals(1, dao.count())
    }

    @Test fun insertingTheSameCanonicalUrlTwiceKeepsOneRow() = withDb { db ->
        val dao = db.itemDao()
        val item = ItemEntity("i1", "s1", "https://ex.com/a", "Title", null, 1L, 2L, null, false, "[]", 0L)
        dao.insertAllIgnoring(listOf(item))
        dao.insertAllIgnoring(listOf(item.copy(id = "i2"))) // same canonical URL: the unique index refuses it
        assertEquals(1, dao.count())
    }

    private fun createV3Database(path: String) {
        val conn: SQLiteConnection = driver.open(path)
        try {
            for (sql in V3_SCHEMA) conn.execSQL(sql)
            conn.execSQL(
                "INSERT INTO clippings (itemId, title, sourceId, sourceTitle, author, canonicalUrl, topicKey, publishedAt, savedAt, excerpt) " +
                    "VALUES ('item-1', 'Flash floods on the Nepal-Tibet border', 'src-1', 'The Guardian', 'A Reporter', " +
                    "'https://example.invalid/a', 'conflict', 1000, 2000, 'An excerpt.')",
            )
            conn.execSQL("INSERT INTO read_events (itemId, openedAt, dwellBucket, viaRiver) VALUES ('item-1', 2000, 'MEDIUM', 0)")
            conn.execSQL("PRAGMA user_version = 3")
        } finally {
            conn.close()
        }
    }

    private companion object {
        /** Verbatim from `schemas/xyz.mdhv.riverwip.data.db.RiverDatabase/3.json`, including Room's identity-hash row. */
        val V3_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `sources` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `tier` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `etag` TEXT, `lastModified` TEXT, `lastFetchAt` INTEGER, `lastError` TEXT, `consecutiveFailures` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_sources_url` ON `sources` (`url`)",
            "CREATE TABLE IF NOT EXISTS `items` (`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `canonicalUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT, `publishedAt` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, `summary` TEXT, `fullTextCached` INTEGER NOT NULL, `topicsJson` TEXT NOT NULL, `simhash` INTEGER NOT NULL, `imageUrl` TEXT, `declaredNsfw` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_items_canonicalUrl` ON `items` (`canonicalUrl`)",
            "CREATE INDEX IF NOT EXISTS `index_items_sourceId` ON `items` (`sourceId`)",
            "CREATE INDEX IF NOT EXISTS `index_items_publishedAt` ON `items` (`publishedAt`)",
            "CREATE TABLE IF NOT EXISTS `read_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` TEXT NOT NULL, `openedAt` INTEGER NOT NULL, `dwellBucket` TEXT NOT NULL, `viaRiver` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_read_events_itemId` ON `read_events` (`itemId`)",
            "CREATE INDEX IF NOT EXISTS `index_read_events_openedAt` ON `read_events` (`openedAt`)",
            "CREATE TABLE IF NOT EXISTS `weekly_aggregates` (`weekStart` INTEGER NOT NULL, `streamCountsByTopicJson` TEXT NOT NULL, `readCountsByTopicJson` TEXT NOT NULL, `sourceCountsJson` TEXT NOT NULL, PRIMARY KEY(`weekStart`))",
            "CREATE TABLE IF NOT EXISTS `clippings` (`itemId` TEXT NOT NULL, `title` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `sourceTitle` TEXT, `author` TEXT, `canonicalUrl` TEXT NOT NULL, `topicKey` TEXT NOT NULL, `publishedAt` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, `excerpt` TEXT, PRIMARY KEY(`itemId`))",
            "CREATE INDEX IF NOT EXISTS `index_clippings_savedAt` ON `clippings` (`savedAt`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ff5f98b17f8139a5fa000cdf79071bb7')",
        )
    }
}
