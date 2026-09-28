package xyz.mdhv.riverwip.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Epoch-millisecond constants below were computed independently (Python `datetime`), not with this code. */
class CivilTimeTest {

    @Test fun civilDayConversionRoundTrips() {
        assertEquals(0L, CivilTime.daysFromCivil(1970, 1, 1))
        assertEquals(Triple(1970, 1, 1), CivilTime.civilFromDays(0))
        assertEquals(Triple(2024, 2, 29), CivilTime.civilFromDays(CivilTime.daysFromCivil(2024, 2, 29)))
        assertEquals(Triple(1969, 12, 31), CivilTime.civilFromDays(-1))
        for (d in -800_000L..800_000L step 997) {
            val (y, m, day) = CivilTime.civilFromDays(d)
            assertEquals(d, CivilTime.daysFromCivil(y, m, day))
        }
    }

    @Test fun parsesRfc1123() {
        assertEquals(1727874000000L, CivilTime.parseRfc1123("Wed, 02 Oct 2024 13:00:00 GMT"))
        assertEquals(1727874000000L - (5 * 3600 + 30 * 60) * 1000L, CivilTime.parseRfc1123("Wed, 2 Oct 2024 13:00:00 +0530"))
        assertEquals(1727874000000L, CivilTime.parseRfc1123("02 Oct 2024 13:00 GMT"))
        assertNull(CivilTime.parseRfc1123("Tue, 02 Oct 2024 13:00:00 GMT"), "wrong weekday is rejected")
        assertNull(CivilTime.parseRfc1123("Wed, 02 Oct 2024 13:00:00 EST"), "named zones other than GMT are rejected")
        assertNull(CivilTime.parseRfc1123("not a date"))
    }

    @Test fun parsesIsoOffsets() {
        assertEquals(1727874000000L, CivilTime.parseIsoOffset("2024-10-02T13:00:00Z"))
        assertEquals(1727874000123L, CivilTime.parseIsoOffset("2024-10-02T13:00:00.123456789Z"))
        assertEquals(1727874000000L - (5 * 3600 + 30 * 60) * 1000L, CivilTime.parseIsoOffset("2024-10-02T13:00:00+05:30"))
        assertEquals(1727874000000L, CivilTime.parseIsoOffset("2024-10-02T13:00Z"))
        assertNull(CivilTime.parseIsoOffset("2024-10-02T18:30:00+05:30[Asia/Kolkata]"), "Java's zoned suffix is not accepted")
        assertNull(CivilTime.parseRfc1123("Wed, 02 Oct 24 13:00:00 GMT"), "two-digit years are not guessed")
        assertEquals(1727913600000L, CivilTime.parseRfc1123("Wed, 02 Oct 2024 24:00:00 GMT"), "24:00 is midnight at day end")
        assertNull(CivilTime.parseIsoOffset("2024-02-30T13:00:00Z"))
        assertNull(CivilTime.parseIsoOffset("2024-10-02T13:00:00+0530"))
        assertNull(CivilTime.parseIsoOffset("2024-10-02T13:00:00"))
        assertNull(CivilTime.parseIsoOffset("2024-10-02 13:00:00Z"))
    }

    @Test fun parsesAndFormatsGdeltDates() {
        assertEquals(1784073600000L, CivilTime.parseGdelt("20260715T000000Z"))
        assertEquals(1767596889000L, CivilTime.parseGdelt("20260105T070809Z"))
        assertNull(CivilTime.parseGdelt("2026-07-15"))
        assertEquals("20260715000000", CivilTime.formatCompactUtc(1784073600000L))
        assertEquals("19691231235959", CivilTime.formatCompactUtc(-1000L))
    }
}

class XmlLiteTest {

    @Test fun readsElementsAttributesTextAndCdata() {
        val root = XmlLite.parse(
            """<?xml version="1.0" encoding="UTF-8"?>
            <!-- c --><rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel><title>A &amp; B &#233; &#x1F600;</title>
                <item><dc:creator>me</dc:creator><description><![CDATA[<p>hi & bye</p>]]></description>
                <enclosure url='http://x/y.jpg' type="image/jpeg"/></item>
              </channel></rss>""",
        )
        assertEquals("rss", root.name)
        assertEquals("2.0", root.attributes["version"])
        val channel = root.childElements.single()
        assertEquals("A & B é 😀", channel.childElements.first { it.name == "title" }.textContent)
        val item = channel.childElements.first { it.name == "item" }
        assertEquals("creator", item.childElements.first().localName)
        assertEquals("<p>hi & bye</p>", item.childElements.first { it.name == "description" }.textContent)
        assertEquals("http://x/y.jpg", item.childElements.first { it.name == "enclosure" }.attributes["url"])
    }

    @Test fun textContentIncludesNestedText() {
        assertEquals("ab", XmlLite.parse("<a>a<b>b</b></a>").textContent)
    }

    @Test fun rejectsWhatTheJdkParserRejected() {
        val bad = listOf(
            "", "<not xml", "<a><b></a>", "<a>&nbsp;</a>", "<a></b>", "<a/><b/>", "text<a/>", "<a/>tail",
            "<a x=1/>", "<a x='1' x='2'/>", "<a x='<'/>", "<a><![CDATA[oops</a>",
            "<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><a>&x;</a>",
            "<!DOCTYPE a><a/>", "<a>&#0;</a>", "<a>&#xD800;</a>", "<a>&amp</a>",
        )
        for (doc in bad) assertFailsWith<XmlParseException>("should reject: $doc") { XmlLite.parse(doc) }
    }

    @Test fun doesNotExpandEntitiesFromADoctype() {
        // The reason DOCTYPE is refused outright: there is no entity table to abuse.
        val evil = """<!DOCTYPE lolz [<!ENTITY a "aaaaaaaaaa"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">]><lolz>&b;</lolz>"""
        assertFailsWith<XmlParseException> { XmlLite.parse(evil) }
    }

    @Test fun capsNestingDepth() {
        val depth = 5_000
        val deep = "<a>".repeat(depth) + "</a>".repeat(depth)
        assertFailsWith<XmlParseException> { XmlLite.parse(deep) }
        val ok = "<a>".repeat(100) + "</a>".repeat(100)
        assertTrue(XmlLite.parse(ok).childElements.isNotEmpty())
    }

    @Test fun normalisesLineEndingsAndAttributeWhitespace() {
        assertEquals("a\nb", XmlLite.parse("<a>a\r\nb</a>").textContent)
        assertEquals("x y z", XmlLite.parse("<a v=\"x\ty\nz\"/>").attributes["v"])
    }
}

class HashingVectorsTest {
    /** Published FNV-1a 64-bit test vectors (Fowler/Noll/Vo). */
    @Test fun fnv1a64MatchesPublishedVectors() {
        assertEquals("cbf29ce484222325", Hashing.fnv1a64Hex(""))
        assertEquals("af63dc4c8601ec8c", Hashing.fnv1a64Hex("a"))
        assertEquals("85944171f73967e8", Hashing.fnv1a64Hex("foobar"))
    }

    /** Ids are derived from these, so every platform must hash the same UTF-8 bytes (values computed independently in Python). */
    @Test fun hashesUtf8BytesIdenticallyOnEveryPlatform() {
        assertEquals("0ac21707b7181e01", Hashing.fnv1a64Hex("é"))
        assertEquals("feff073875020288", Hashing.fnv1a64Hex("😀"))
        assertEquals("b6406fde598bc079", Hashing.fnv1a64Hex("नमस्ते"))
    }
}
