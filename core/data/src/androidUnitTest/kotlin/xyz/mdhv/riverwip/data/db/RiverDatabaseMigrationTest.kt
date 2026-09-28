package xyz.mdhv.riverwip.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The database's first real migration, tested rather than asserted (D37).
 *
 * This matters more than a schema change usually would. Until v4 the database
 * was built with `fallbackToDestructiveMigration()` alone, under a comment
 * saying the schema was "unshipped" — which stopped being true at versionCode
 * 2. Bumping the version without a migration would therefore have answered the
 * upgrade by **deleting every existing reader's clippings, read events and
 * weekly aggregates**: the entire history the Loom draws from, and the part of
 * this app the brief calls the reader's own. So the thing under test here is
 * not "does the new table appear" but "is the old data still there afterwards".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RiverDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RiverDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratingFromV3KeepsTheReadersOwnData() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO clippings
                  (itemId, title, sourceId, sourceTitle, author, canonicalUrl, topicKey, publishedAt, savedAt, excerpt)
                VALUES
                  ('item-1', 'Flash floods on the Nepal-Tibet border', 'src-1', 'The Guardian',
                   'A Reporter', 'https://example.invalid/a', 'conflict', 1000, 2000, 'An excerpt.')
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO read_events (itemId, openedAt, dwellBucket, viaRiver) " +
                    "VALUES ('item-1', 2000, 'MEDIUM', 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, RiverDatabase.MIGRATION_3_4)

        db.query("SELECT title FROM clippings WHERE itemId = 'item-1'").use { c ->
            assertTrue("the clipping survived the migration", c.moveToFirst())
            assertEquals("Flash floods on the Nepal-Tibet border", c.getString(0))
        }
        db.query("SELECT COUNT(*) FROM read_events").use { c ->
            c.moveToFirst()
            assertEquals("read events survived the migration", 1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM article_text").use { c ->
            assertTrue("the new index exists and is queryable", c.moveToFirst())
            assertEquals("and starts empty", 0, c.getInt(0))
        }
        db.close()
    }

    /**
     * The tokenizer is the part of this most likely to be quietly wrong, and
     * the part no amount of reading the DDL would catch. FTS4's default
     * `simple` tokenizer treats every non-ASCII byte as a separator, which
     * would shred the scripts the catalogue just gained feeds in — the index
     * would build, queries would run, and Telugu would simply never match.
     */
    @Test
    fun ftsIndexFindsBothLatinAndIndicScriptsByPrefix() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RiverDatabase::class.java).build()
        val dao = db.articleTextDao()

        dao.insert(ArticleTextEntity(itemId = "en", body = "Two dozen states filed a fresh lawsuit on Wednesday."))
        dao.insert(ArticleTextEntity(itemId = "te", body = "ఆంధ్ర ప్రదేశ్ లో భారీ వర్షాలు కురిశాయి"))
        dao.insert(ArticleTextEntity(itemId = "hi", body = "मोदी और पुतिन के बीच बैठक हुई"))

        // Prefix matching is what ArticleSearch.toMatchQuery asks for.
        assertEquals(listOf("en"), dao.search("lawsuit*", 10).map { it.itemId })
        assertEquals(listOf("en"), dao.search("laws*", 10).map { it.itemId })
        assertEquals(listOf("te"), dao.search("ఆంధ్ర*", 10).map { it.itemId })
        assertEquals(listOf("hi"), dao.search("पुतिन*", 10).map { it.itemId })
        // Multiple terms narrow, and they are joined by whitespace — which
        // is how standard FTS4 query syntax spells AND. Spelling it "AND"
        // instead would search for the literal word "and" as a third term.
        assertEquals(listOf("en"), dao.search("dozen* lawsuit*", 10).map { it.itemId })
        assertTrue("a term the article lacks excludes it", dao.search("dozen* monsoon*", 10).isEmpty())
        assertTrue(
            "spelling AND out loud would demand the literal word",
            dao.search("dozen* AND lawsuit*", 10).isEmpty(),
        )

        db.close()
    }

    /** Re-indexing an article must replace it, not add a second copy. */
    @Test
    fun reindexingAnArticleDoesNotDuplicateIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RiverDatabase::class.java).build()
        val dao = db.articleTextDao()

        dao.insert(ArticleTextEntity(itemId = "a", body = "first extraction mentions monsoon"))
        dao.deleteFor("a")
        dao.insert(ArticleTextEntity(itemId = "a", body = "second extraction mentions monsoon"))

        val hits = dao.search("monsoon*", 10)
        assertEquals("exactly one row for the article", 1, hits.size)
        assertTrue("and it is the newer body", hits.single().body.startsWith("second"))
        assertEquals(1, dao.count())

        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
