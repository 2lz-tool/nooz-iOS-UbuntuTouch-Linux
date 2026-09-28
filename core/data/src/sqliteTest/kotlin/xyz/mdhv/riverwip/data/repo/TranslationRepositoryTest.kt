package xyz.mdhv.riverwip.data.repo

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.buffer
import okio.use
import xyz.mdhv.riverwip.data.TestPlatform
import xyz.mdhv.riverwip.model.TranslationCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real install-then-query path against a real SQLite file in
 * WikDict's actual shape -- built here rather than downloaded, so the test needs
 * no network and no multi-megabyte fixture, but goes through the same staging,
 * indexing, rename and lookup code a real download does.
 *
 * The schema and the two behaviours that matter (pipe-separated `trans_list`,
 * case-sensitive exact-match headwords) were read off a genuine WikDict export
 * during the 2026-09-01 verification run, not assumed.
 */
class TranslationRepositoryTest {

    private fun withPlatform(block: suspend (TestPlatform) -> Unit) = runTest {
        val platform = TestPlatform()
        try { block(platform) } finally { platform.cleanUp() }
    }

    /** Writes a file with WikDict's own table shape and a handful of entries. */
    private fun writeWikDictLike(platform: TestPlatform, name: String, rows: List<Triple<String, String, Double>>): Path {
        val dest = platform.root / name
        val db: SQLiteConnection = BundledSQLiteDriver().open(dest.toString())
        try {
            db.execSQL("CREATE TABLE simple_translation(written_rep TEXT, trans_list, max_score, rel_importance)")
            for ((word, trans, score) in rows) {
                val st = db.prepare("INSERT INTO simple_translation(written_rep, trans_list, max_score, rel_importance) VALUES (?, ?, ?, ?)")
                try {
                    st.bindText(1, word); st.bindText(2, trans); st.bindDouble(3, score); st.bindDouble(4, 1.0)
                    st.step()
                } finally { st.close() }
            }
        } finally { db.close() }
        return dest
    }

    private class FakeDownloader(private val platform: TestPlatform, private val source: Path) : TranslationDownloader {
        var calls = 0
        override suspend fun fetchTo(url: String, dest: Path) {
            calls++
            val fs = platform.fileSystem
            fs.source(source).buffer().use { src -> fs.sink(dest).buffer().use { it.writeAll(src) } }
        }
    }

    private fun option() = TranslationCatalog.byId("en-es")!!

    @Test fun downloadsInstallsAndTranslates() = withPlatform { p ->
        val staged = writeWikDictLike(p, "src-en-es.sqlite3", listOf(Triple("water", "agua | riego", 3.0), Triple("newspaper", "periódico", 2.0)))
        val repo = TranslationRepository(p, FakeDownloader(p, staged))

        assertNull(repo.observeInstalledId().first(), "nothing installed to begin with")
        assertEquals(emptyList(), repo.translate("water"))

        assertTrue(repo.download(option()).isSuccess)

        assertEquals("en-es", repo.observeInstalledId().first())
        assertEquals(option(), repo.installedOption())
        assertEquals(listOf("agua", "riego"), repo.translate("water"))
        assertEquals(listOf("periódico"), repo.translate("newspaper"))
    }

    @Test fun aWordTappedAtTheStartOfASentenceStillResolves() = withPlatform { p ->
        // WikDict matches headwords exactly and stores them lowercase, so "Water" -- which is what a reader
        // long-presses at the start of a sentence -- finds nothing without the case fallback.
        val staged = writeWikDictLike(p, "src-case.sqlite3", listOf(Triple("water", "agua", 3.0)))
        val repo = TranslationRepository(p, FakeDownloader(p, staged))
        repo.download(option())

        assertEquals(listOf("agua"), repo.translate("Water"))
        assertEquals(listOf("agua"), repo.translate("  water  "))
        assertEquals(emptyList(), repo.translate("unlisted"))
        assertEquals(emptyList(), repo.translate("   "))
    }

    @Test fun downloadBuildsTheLookupIndexWikDictOmits() = withPlatform { p ->
        // WikDict ships simple_translation with no index on written_rep, so every lookup is a full scan until we add one.
        val staged = writeWikDictLike(p, "src-index.sqlite3", listOf(Triple("water", "agua", 3.0)))
        TranslationRepository(p, FakeDownloader(p, staged)).download(option())

        val installed = p.filesDir / "translations" / "en-es.sqlite3"
        val db = BundledSQLiteDriver().open(installed.toString())
        try {
            val st = db.prepare("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='simple_translation'")
            try { assertTrue(st.step(), "an index was created") } finally { st.close() }
        } finally { db.close() }
    }

    @Test fun aFailedDownloadLeavesNothingInstalled() = withPlatform { p ->
        val repo = TranslationRepository(p, object : TranslationDownloader {
            override suspend fun fetchTo(url: String, dest: Path) = error("network down")
        })

        val result = repo.download(option())

        assertTrue(result.isFailure, "the failure is reported, not swallowed")
        assertNull(repo.observeInstalledId().first(), "and nothing is marked installed")
        // No half-written file is left behind wearing the real name -- that would fail as a corrupt database on every
        // lookup forever, rather than as one failed download.
        val dir = p.filesDir / "translations"
        assertTrue(!p.fileSystem.exists(dir) || p.fileSystem.list(dir).isEmpty(), "no leftovers")
        assertEquals(emptyList(), repo.translate("water"))
    }

    @Test fun aCorruptFileYieldsNoTranslationRatherThanACrash() = withPlatform { p ->
        val staged = writeWikDictLike(p, "src-ok.sqlite3", listOf(Triple("water", "agua", 3.0)))
        val repo = TranslationRepository(p, FakeDownloader(p, staged))
        repo.download(option())

        // Something eats the file after install -- storage pressure, a half-finished restore.
        p.fileSystem.write(p.filesDir / "translations" / "en-es.sqlite3") { writeUtf8("not a database") }
        assertEquals(emptyList(), repo.translate("water"))
    }

    @Test fun installingASecondDictionaryReplacesTheFirst() = withPlatform { p ->
        val first = writeWikDictLike(p, "src-first.sqlite3", listOf(Triple("water", "agua", 3.0)))
        val repo = TranslationRepository(p, FakeDownloader(p, first))
        repo.download(TranslationCatalog.byId("en-es")!!)

        val second = writeWikDictLike(p, "src-second.sqlite3", listOf(Triple("water", "eau", 3.0)))
        TranslationRepository(p, FakeDownloader(p, second)).download(TranslationCatalog.byId("en-fr")!!)

        assertEquals(1, p.fileSystem.list(p.filesDir / "translations").size, "only one dictionary is kept")
        assertEquals("en-fr", repo.observeInstalledId().first())
        assertEquals(listOf("eau"), repo.translate("water"))
    }

    @Test fun removeForgetsAndDeletes() = withPlatform { p ->
        val staged = writeWikDictLike(p, "src-remove.sqlite3", listOf(Triple("water", "agua", 3.0)))
        val repo = TranslationRepository(p, FakeDownloader(p, staged))
        repo.download(option())

        repo.remove()

        assertNull(repo.observeInstalledId().first())
        assertEquals(emptyList(), repo.translate("water"))
        assertTrue(p.fileSystem.list(p.filesDir / "translations").isEmpty())
    }
}
