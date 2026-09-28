package xyz.mdhv.riverwip.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class TranslationCatalogTest {

    @Test fun everyOptionIsWellFormedAndAddressable() {
        assertTrue(TranslationCatalog.options.isNotEmpty(), "catalogue is not empty")
        for (o in TranslationCatalog.options) {
            assertEquals("${o.sourceLang}-${o.targetLang}", o.id, "id is source-target: ${o.id}")
            assertTrue(o.downloadUrl.startsWith("https://"), "https url: ${o.id}")
            assertTrue(o.downloadUrl.endsWith("/${o.id}.sqlite3"), "url ends in the pair: ${o.id}")
            assertTrue(o.approxSizeBytes > 0, "has a size: ${o.id}")
            assertTrue(o.license.contains("CC BY-SA"), "attributes its source: ${o.id}")
            assertEquals(o, TranslationCatalog.byId(o.id))
        }
    }

    @Test fun idsAndUrlsAreUnique() {
        val ids = TranslationCatalog.options.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids")
        val urls = TranslationCatalog.options.map { it.downloadUrl }
        assertEquals(urls.size, urls.toSet().size, "duplicate urls")
    }

    @Test fun neverPairsALanguageWithItself() {
        for (o in TranslationCatalog.options) {
            assertTrue(o.sourceLang != o.targetLang, "${o.id} translates between two languages")
        }
    }

    @Test fun bothDirectionsExistForEachPair() {
        // A reader of Spanish articles wanting English needs es-en; a reader of
        // English wanting Spanish needs en-es. Shipping only one direction
        // would silently serve half the readers it looks like it serves.
        val ids = TranslationCatalog.options.map { it.id }.toSet()
        for (o in TranslationCatalog.options) {
            assertTrue("${o.targetLang}-${o.sourceLang}" in ids, "reverse of ${o.id} is present")
        }
    }

    @Test fun lookupsByLanguageAndUnknownIds() {
        val fromEnglish = TranslationCatalog.fromLanguage("en")
        assertTrue(fromEnglish.isNotEmpty(), "English has outbound pairs")
        assertTrue(fromEnglish.all { it.sourceLang == "en" }, "all start from English")
        assertNull(TranslationCatalog.byId("no-such-pair"))
        assertNull(TranslationCatalog.byId(null))
    }

    @Test fun labelsAndSizesAreReadable() {
        val es = TranslationCatalog.byId("en-es")!!
        assertEquals("English → Spanish", es.label)
        assertTrue(es.approxSizeHuman.endsWith("MB"), "megabytes for a big one: ${es.approxSizeHuman}")
        val mg = TranslationCatalog.byId("mg-en")!!
        assertTrue(mg.approxSizeHuman.endsWith("KB"), "kilobytes for a small one: ${mg.approxSizeHuman}")
    }

    @Test fun transListIsSplitOnPipes() {
        assertEquals(listOf("rano", "ranu"), TranslationFormatting.senses("rano | ranu"))
        assertEquals(listOf("gazety"), TranslationFormatting.senses("gazety"))
    }

    @Test fun transListDropsBlanksAndDuplicates() {
        // Both occur in the generated column, and a sheet listing the same word
        // twice reads like a bug rather than like two senses.
        assertEquals(listOf("agua"), TranslationFormatting.senses("agua | agua"))
        assertEquals(listOf("agua", "riego"), TranslationFormatting.senses(" agua |  | riego |"))
        assertEquals(emptyList<String>(), TranslationFormatting.senses(""))
        assertEquals(emptyList<String>(), TranslationFormatting.senses(null))
        assertEquals(emptyList<String>(), TranslationFormatting.senses(" | | "))
    }
}
