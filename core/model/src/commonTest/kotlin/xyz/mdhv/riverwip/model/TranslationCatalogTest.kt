package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationCatalogTest {

    @Test fun everyOptionIsWellFormedAndAddressable() {
        assertTrue("catalogue is not empty", TranslationCatalog.options.isNotEmpty())
        for (o in TranslationCatalog.options) {
            assertEquals("id is source-target: ${o.id}", "${o.sourceLang}-${o.targetLang}", o.id)
            assertTrue("https url: ${o.id}", o.downloadUrl.startsWith("https://"))
            assertTrue("url ends in the pair: ${o.id}", o.downloadUrl.endsWith("/${o.id}.sqlite3"))
            assertTrue("has a size: ${o.id}", o.approxSizeBytes > 0)
            assertTrue("attributes its source: ${o.id}", o.license.contains("CC BY-SA"))
            assertEquals(o, TranslationCatalog.byId(o.id))
        }
    }

    @Test fun idsAndUrlsAreUnique() {
        val ids = TranslationCatalog.options.map { it.id }
        assertEquals("duplicate ids", ids.size, ids.toSet().size)
        val urls = TranslationCatalog.options.map { it.downloadUrl }
        assertEquals("duplicate urls", urls.size, urls.toSet().size)
    }

    @Test fun neverPairsALanguageWithItself() {
        for (o in TranslationCatalog.options) {
            assertTrue("${o.id} translates between two languages", o.sourceLang != o.targetLang)
        }
    }

    @Test fun bothDirectionsExistForEachPair() {
        // A reader of Spanish articles wanting English needs es-en; a reader of
        // English wanting Spanish needs en-es. Shipping only one direction
        // would silently serve half the readers it looks like it serves.
        val ids = TranslationCatalog.options.map { it.id }.toSet()
        for (o in TranslationCatalog.options) {
            assertTrue("reverse of ${o.id} is present", "${o.targetLang}-${o.sourceLang}" in ids)
        }
    }

    @Test fun lookupsByLanguageAndUnknownIds() {
        val fromEnglish = TranslationCatalog.fromLanguage("en")
        assertTrue("English has outbound pairs", fromEnglish.isNotEmpty())
        assertTrue("all start from English", fromEnglish.all { it.sourceLang == "en" })
        assertNull(TranslationCatalog.byId("no-such-pair"))
        assertNull(TranslationCatalog.byId(null))
    }

    @Test fun labelsAndSizesAreReadable() {
        val es = TranslationCatalog.byId("en-es")!!
        assertEquals("English → Spanish", es.label)
        assertTrue("megabytes for a big one: ${es.approxSizeHuman}", es.approxSizeHuman.endsWith("MB"))
        val mg = TranslationCatalog.byId("mg-en")!!
        assertTrue("kilobytes for a small one: ${mg.approxSizeHuman}", mg.approxSizeHuman.endsWith("KB"))
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
