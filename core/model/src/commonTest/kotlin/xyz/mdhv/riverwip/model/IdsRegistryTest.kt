package xyz.mdhv.riverwip.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsRegistryTest {

    @Test fun sourceIdStableAndKindScoped() {
        val a = Ids.sourceId(SourceKind.RSS, "https://ex.com/rss")
        val b = Ids.sourceId(SourceKind.RSS, "https://ex.com/rss")
        assertEquals(a, b)
        assertTrue(a.startsWith("src_rss_"))
        // Same url, different kind → different id.
        assertTrue(a != Ids.sourceId(SourceKind.GDELT, "https://ex.com/rss"))
    }

    @Test fun itemIdDerivesFromCanonicalUrl() {
        val canon = CanonicalUrl.canonicalize("https://ex.com/story?utm_source=x")
        val id1 = Ids.itemId(canon)
        val id2 = Ids.itemId(CanonicalUrl.canonicalize("https://ex.com/story"))
        assertEquals(id1, id2) // tracking param collapsed → same item
    }

    @Test fun fnvIsDeterministic() {
        assertEquals(Hashing.fnv1a64("river"), Hashing.fnv1a64("river"))
        assertTrue(Hashing.fnv1a64("a") != Hashing.fnv1a64("b"))
        assertEquals(16, Hashing.fnv1a64Hex("anything").length)
    }

    @Test fun catalogueParsesAndIgnoresUnknownKeys() {
        val json = """
            {
              "version": 1,
              "services": [
                {"id":"bbc","kind":"rss","title":"BBC","tier":"A","url":"https://feeds.bbci.co.uk/news/rss.xml",
                 "region":"global","enabledByDefault":true,"futureField":"ignored"},
                {"id":"guardian","kind":"guardian","title":"Guardian Open Platform","tier":"B",
                 "requiresKey":true,"keySignupUrl":"https://open-platform.theguardian.com/access/",
                 "freeTier":{"requestsPerDay":5000}}
              ]
            }
        """.trimIndent()
        val parser = Json { ignoreUnknownKeys = true }
        val cat = parser.decodeFromString(Catalogue.serializer(), json)
        assertEquals(2, cat.services.size)

        val bbc = cat.services[0]
        assertEquals(SourceKind.RSS, bbc.sourceKind)
        assertEquals(Tier.A, bbc.tierEnum)
        val src = bbc.toSourceOrNull(addedAt = 5L)
        assertNotNull(src)
        assertEquals("https://feeds.bbci.co.uk/news/rss.xml", src!!.url)
        assertTrue(src.enabled)

        val guardian = cat.services[1]
        assertTrue(guardian.requiresKey)
        assertEquals(5000, guardian.freeTier?.requestsPerDay)
        // Keyed builder entry has no concrete url → not directly addable.
        assertNull(guardian.toSourceOrNull(addedAt = 5L))
    }
}
