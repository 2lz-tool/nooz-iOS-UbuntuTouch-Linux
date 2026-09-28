package xyz.mdhv.riverwip.model

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The JDK classes this module used to rely on (`java.net.URI`, `URLEncoder`,
 * `java.time`, the DOM parser) are the oracle here. The portable replacements in
 * `commonMain` must give the same answers, so the already-ingested data and the
 * behaviour the 215 original tests pin down carry over to every platform.
 *
 * JVM-only on purpose: these need the JDK. Seeds are fixed so a failure is reproducible.
 */
class PortableParityTest {

    // ---- percent-encoding ------------------------------------------------

    @Test fun urlEncodingMatchesUrlEncoder() {
        val rnd = Random(20260928)
        val samples = mutableListOf(
            "", " ", "hello world", "a+b=c&d", "ünïcödé", "日本語 テキスト", "😀 emoji", "~!@#$%^&*()_+{}|:\"<>?`-=[]\\;',./",
        )
        repeat(3000) { samples += randomString(rnd, 0, 24) }
        for (s in samples) {
            assertEquals(URLEncoder.encode(s, "UTF-8").replace("+", "%20"), UrlEncoding.encode(s), "encode(${s.escaped()})")
        }
    }

    // ---- URI parsing -----------------------------------------------------

    private val uriCorpus = listOf(
        "https://example.com/a/b?x=1#frag", "HTTP://EXAMPLE.com:80/", "https://user:pw@host.example:8443/p",
        "https://my_host.example/x", "https://münchen.de/x", "https://example.com/a b", "https://example.com/a%zz",
        "https://example.com/a%20b", "https://example.com/?q[]=1", "https://example.com/a[0]", "https://[::1]:8080/x",
        "https://192.168.0.1/x", "https://example.com:abc/x", "https://example.com.:443/x", "mailto:a@b.c",
        "//cdn.example.com/x", "/relative/path", "relative", "../x", "?q=1", "#frag", "", "https://", "https:///x",
        "file:///etc/hosts", "https://example.com:99999999999/x", "https://-bad.example/x", "https://exa mple.com",
        "https://a.b.c.d.e/x?x=1?2#3#4", "feed:https://example.com/rss", "https://example.com/p?a=b&c=d%20e",
        "https://example.com/café", "https://example.com:/x", "https://example.com:0/x", "https://1.2.3.4.5/x",
        "https://example.com/a;b=c/d", "urn:isbn:0451450523", "http://a/b\\c", "http://a/<x>", "https://x.y/%E2%82%AC",
        "https://EXAMPLE.co.uk/Path/With/Caps", "https://sub-domain.example.org/x", "https://exa_mple.org/x",
        "https://user@/x", "http://example.com/#", "http://example.com/?", "https://example.com:443", "1abc:foo", ":nothing",
    )

    @Test fun uriParseMatchesJavaNetUri() {
        for (s in uriCorpus) {
            val jdk = runCatching { URI(s) }
            val mine = runCatching { Uri.parse(s) }
            if (jdk.isFailure) {
                assertTrue(mine.isFailure, "JDK rejects but Uri.parse accepts: ${s.escaped()}")
                continue
            }
            val j = jdk.getOrThrow()
            val m = mine.getOrElse { fail("JDK accepts but Uri.parse rejects: ${s.escaped()} -> $it") }
            assertEquals(j.isOpaque, m.isOpaque, "opaque: $s")
            assertEquals(j.scheme, m.scheme, "scheme: $s")
            assertEquals(j.host, m.host, "host: $s")
            assertEquals(j.port, m.port, "port: $s")
            assertEquals(j.rawPath ?: "", m.rawPath, "path: $s")
            if (!j.isOpaque) assertEquals(j.rawQuery, m.rawQuery, "query: $s")
            assertEquals(j.rawFragment, m.rawFragment, "fragment: $s")
        }
    }

    @Test fun uriParseFuzzAgreesOnAcceptAndComponents() {
        val rnd = Random(7)
        val alphabet = "abcXYZ019-._~:/?#[]@!$&'()*+,;=% \"<>\\^`{|}éあ"
        val seeds = listOf("https://example.com/a/b?x=1#f", "http://user@host.example:81/p;q?r#s", "//h/p", "a:b/c", "/x/y")
        var compared = 0
        val mismatches = ArrayList<String>()
        repeat(6000) {
            val chars = seeds[rnd.nextInt(seeds.size)].toMutableList()
            repeat(rnd.nextInt(1, 4)) {
                when (rnd.nextInt(3)) {
                    0 -> if (chars.isNotEmpty()) chars.removeAt(rnd.nextInt(chars.size))
                    1 -> chars.add(rnd.nextInt(chars.size + 1), alphabet[rnd.nextInt(alphabet.length)])
                    else -> if (chars.isNotEmpty()) chars[rnd.nextInt(chars.size)] = alphabet[rnd.nextInt(alphabet.length)]
                }
            }
            val s = chars.joinToString("")
            val jdk = runCatching { URI(s) }
            val mine = runCatching { Uri.parse(s) }
            if (jdk.isSuccess != mine.isSuccess) {
                mismatches += "accept/reject: ${s.escaped()} jdk=${jdk.exceptionOrNull()?.message ?: "ok"} mine=${mine.exceptionOrNull()?.message ?: "ok"}"
            } else if (jdk.isSuccess) {
                val j = jdk.getOrThrow(); val m = mine.getOrThrow()
                val a = listOf(j.scheme, j.host, j.port, j.rawPath ?: "", j.rawFragment, if (j.isOpaque) null else j.rawQuery)
                val b = listOf(m.scheme, m.host, m.port, m.rawPath, m.rawFragment, if (m.isOpaque) null else m.rawQuery)
                if (a != b) mismatches += "components: ${s.escaped()} jdk=$a mine=$b"
                compared++
            }
        }
        assertTrue(mismatches.isEmpty(), "${mismatches.size} mismatch(es):\n" + mismatches.distinct().take(25).joinToString("\n"))
        assertTrue(compared > 500, "fuzz should exercise many accepted URIs, got $compared")
    }

    @Test fun uriResolveMatchesJavaNetUriForRealisticReferences() {
        val bases = listOf("https://example.com/blog/post/index.html?x=1", "https://example.com", "http://a/b/c/d;p?q", "https://example.com/dir/")
        val refs = listOf(
            "/feed.xml", "feed.xml", "../rss", "./atom", "//cdn.example.com/rss", "https://other.example/feed", "feed.xml?x=2#f",
            "#top", "/", "..", "../..", "a/./b/../c", "g;x", "rss?format=atom", "feed/", "./", "x/y/z.xml",
        )
        for (b in bases) for (r in refs) {
            val expected = runCatching { URI(b).resolve(r).toString() }.getOrNull()
            val actual = runCatching { Uri.parse(b).resolve(r) }.getOrNull()
            // Documented deviation: java.net.URI leaves `..` segments that climb above the root in place; RFC 3986 drops them.
            if (expected != null && ("/../" in expected || expected.endsWith("/.."))) {
                assertTrue(actual != null && "/../" !in actual && !actual.endsWith("/.."), "resolve($r) against $b -> $actual")
                continue
            }
            assertEquals(expected, actual, "resolve($r) against $b")
        }
    }

    // ---- dates -----------------------------------------------------------

    /** The exact logic `FeedParser.parseDate` had before it lost its `java.time` dependency. */
    private fun oracleParseDate(raw: String?): Long? {
        val s = raw?.trim()?.ifBlank { null } ?: return null
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s).toInstant().toEpochMilli() }
        return null
    }

    private val dateCorpus = listOf(
        "Wed, 02 Oct 2024 13:00:00 GMT", "Wed, 2 Oct 2024 13:00:00 +0530", "02 Oct 2024 13:00 GMT", "Tue, 02 Oct 2024 13:00:00 GMT",
        "Wed, 02 Oct 2024 13:00:00 EST", "Wed, 02 Oct 2024 13:00:00 Z", "Wed, 02 Oct 2024 13:00:00 UT", "Thu, 31 Oct 2024 23:59:59 -0800",
        "Wed, 32 Oct 2024 13:00:00 GMT", "Sat, 30 Feb 2024 13:00:00 GMT", "Fri, 29 Feb 2024 13:00:00 GMT", "wed, 02 oct 2024 13:00:00 gmt",
        "WED, 02 OCT 2024 13:00:00 GMT", "Wed,02 Oct 2024 13:00:00 GMT", "Wed, 02 Oct 2024 13:00:60 GMT",
        "Wed, 02 Oct 2024 24:00:00 GMT", "Wed, 02 Oct 2024 13:00:00 +05:30", "Wed, 02 Oct 2024 13:00:00 GMT+1", "  Wed, 02 Oct 2024 13:00:00 GMT  ",
        "2024-10-02T13:00:00Z", "2024-10-02T13:00:00.123456789Z", "2024-10-02T13:00:00.1Z", "2024-10-02T13:00:00+05:30", "2024-10-02T13:00Z",
        "2024-10-02T13:00:00-08:00", "2024-02-30T13:00:00Z", "2024-10-02T13:00:00+0530", "2024-10-02T13:00:00", "2024-10-02 13:00:00Z",
        "2024-10-02t13:00:00z", "2024-10-02T24:00:00Z", "2024-10-02T13:00:60Z",
        "2024-10-02T13:00:00+18:00", "2024-10-02T13:00:00+19:00", "2024-10-02T13:00:00+05:30:15", "2024-10-02", "10/02/2024", "yesterday",
        "", "   ", "2024-13-02T13:00:00Z", "2024-10-32T13:00:00Z", "1999-12-31T23:59:59Z", "0001-01-01T00:00:00Z", "2100-01-01T00:00:00Z",
    )

    @Test fun parseDateMatchesJavaTimeOnCorpus() {
        val mismatches = dateCorpus.filter { oracleParseDate(it) != FeedParser.parseDate(it) }
            .map { "parseDate(${it.escaped()}): jdk=${oracleParseDate(it)} mine=${FeedParser.parseDate(it)}" }
        assertTrue(mismatches.isEmpty(), "${mismatches.size} mismatch(es):\n" + mismatches.joinToString("\n"))
        assertEquals(null, FeedParser.parseDate(null))
    }

    @Test fun parseDateMatchesJavaTimeOnGeneratedDates() {
        val rnd = Random(99)
        val rfc = DateTimeFormatter.RFC_1123_DATE_TIME
        repeat(4000) {
            val seconds = rnd.nextLong(0, 4_102_444_800L) // 1970 .. 2100
            val offset = ZoneOffset.ofTotalSeconds(if (rnd.nextBoolean()) 0 else rnd.nextInt(-18 * 60, 18 * 60 + 1) * 60)
            val zdt = Instant.ofEpochSecond(seconds).atZone(offset)
            val nanos = if (rnd.nextBoolean()) 0 else rnd.nextInt(0, 1_000_000_000)
            val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zdt.withNano(nanos))
            val r = rfc.format(zdt)
            for (s in listOf(iso, r)) assertEquals(oracleParseDate(s), FeedParser.parseDate(s), "parseDate(${s})")
        }
    }

    @Test fun parseDateAgreesOnMutatedDates() {
        val rnd = Random(1234)
        val alphabet = "0123456789:-+.,TZ []GMTabcWedOct"
        var accepted = 0
        val wrong = ArrayList<String>()
        val missed = ArrayList<String>()
        repeat(8000) {
            val chars = dateCorpus[rnd.nextInt(dateCorpus.size)].toMutableList()
            repeat(rnd.nextInt(1, 3)) {
                when (rnd.nextInt(3)) {
                    0 -> if (chars.isNotEmpty()) chars.removeAt(rnd.nextInt(chars.size))
                    1 -> chars.add(rnd.nextInt(chars.size + 1), alphabet[rnd.nextInt(alphabet.length)])
                    else -> if (chars.isNotEmpty()) chars[rnd.nextInt(chars.size)] = alphabet[rnd.nextInt(alphabet.length)]
                }
            }
            val s = chars.joinToString("")
            val expected = oracleParseDate(s)
            val actual = FeedParser.parseDate(s)
            if (expected != null) accepted++
            // Never invent or shift a date the JDK would not have produced ...
            if (actual != null && actual != expected) wrong += "parseDate(${s.escaped()}): jdk=$expected mine=$actual"
            // ... and only give up on exotic forms the JDK's lenient formatter tolerated.
            if (expected != null && actual == null && expected in 0L..4_102_444_800_000L) missed += s.escaped()
        }
        assertTrue(wrong.isEmpty(), "${wrong.size} wrong date(s):\n" + wrong.distinct().take(25).joinToString("\n"))
        assertTrue(accepted > 200, "fuzz should keep exercising accepted dates, got $accepted")
        // What is left are forms only java.time's lenient number parsing tolerated (zero-padded or extra digits such as
        // minute "0000", year "02024", day "+2"). No feed emits them; well-formed input is covered exactly by the tests above.
        assertTrue(missed.size * 5 < accepted, "too many plausible JDK-accepted dates rejected (${missed.size}/$accepted):\n" + missed.distinct().take(40).joinToString("\n"))
    }

    @Test fun gdeltDatesMatchJavaTime() {
        val out = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)
        val inn = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        val rnd = Random(5)
        repeat(4000) {
            val ms = rnd.nextLong(0, 4_102_444_800_000L)
            assertEquals(out.format(Instant.ofEpochMilli(ms)), FeedUrls.gdeltDateTime(ms), "gdeltDateTime($ms)")
            val s = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(ms))
            val expected = runCatching { LocalDateTime.parse(s, inn).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            assertEquals(expected, FeedParser.parse("""{"articles":[{"url":"https://x/y","seendate":"$s"}]}""").items.single().publishedAtMillis, "seendate $s")
        }
        for (s in listOf("20260231T000000Z", "20260229T000000Z", "20240229T000000Z", "20261301T000000Z", "20260101T240000Z", "20260101T000060Z", "2026-01-01")) {
            val expected = runCatching { LocalDateTime.parse(s, inn).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            assertEquals(expected, CivilTime.parseGdelt(s), "parseGdelt($s)")
        }
    }

    // ---- week bucketing --------------------------------------------------

    /** `WeekBucketing.periodStart` as it was written against `java.time`. */
    private fun oraclePeriodStart(epochMillis: Long, periodDays: Int): Long {
        val epochDay = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        val bucketStart = if (periodDays == 7) {
            val sinceMonday = ((epochDay + 3) % 7 + 7) % 7
            epochDay - sinceMonday
        } else epochDay - (((epochDay % periodDays) + periodDays) % periodDays)
        return Instant.EPOCH.plus(bucketStart, ChronoUnit.DAYS).toEpochMilli()
    }

    @Test fun weekBucketingMatchesJavaTime() {
        val rnd = Random(31)
        repeat(6000) {
            val ms = rnd.nextLong(-4_000_000_000_000L, 4_000_000_000_000L)
            val days = if (rnd.nextInt(3) == 0) 7 else rnd.nextInt(1, 40)
            val expectedStart = oraclePeriodStart(ms, days)
            assertEquals(expectedStart, WeekBucketing.periodStart(ms, days), "periodStart($ms, $days)")
            val expectedEnd = Instant.ofEpochMilli(expectedStart).plus(days.toLong(), ChronoUnit.DAYS).toEpochMilli()
            assertEquals(expectedStart until expectedEnd, WeekBucketing.periodRange(ms, days), "periodRange($ms, $days)")
        }
    }

    // ---- XML -------------------------------------------------------------

    private fun jdkParse(xml: String): Element {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isIgnoringComments = true
        f.isCoalescing = true
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return f.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun canonical(e: Element): String = buildString {
        append('<').append(e.tagName)
        val attrs = e.attributes
        val pairs = (0 until attrs.length).map { attrs.item(it).nodeName to attrs.item(it).nodeValue }.sortedBy { it.first }
        for ((k, v) in pairs) append(' ').append(k).append("=[").append(v).append(']')
        append('>')
        val text = StringBuilder()
        fun flush() { if (text.isNotEmpty()) { append("T[").append(text).append(']'); text.setLength(0) } }
        val kids = e.childNodes
        for (i in 0 until kids.length) {
            val k = kids.item(i)
            when (k.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> text.append(k.nodeValue)
                Node.ELEMENT_NODE -> { flush(); append(canonical(k as Element)) }
            }
        }
        flush()
        append("</").append(e.tagName).append('>')
    }

    private fun canonical(e: XmlElement): String = buildString {
        append('<').append(e.name)
        for ((k, v) in e.attributes.entries.sortedBy { it.key }) append(' ').append(k).append("=[").append(v).append(']')
        append('>')
        val text = StringBuilder()
        fun flush() { if (text.isNotEmpty()) { append("T[").append(text).append(']'); text.setLength(0) } }
        for (k in e.children) when (k) {
            is XmlText -> text.append(k.text)
            is XmlElement -> { flush(); append(canonical(k)) }
        }
        flush()
        append("</").append(e.name).append('>')
    }

    private val xmlCorpus = listOf(
        """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><title>Example</title><item><title>A &amp; B</title><link>https://ex.com/a?x=1&amp;y=2</link><dc:creator>me</dc:creator><description><![CDATA[<p>hi</p>]]></description></item></channel></rss>""",
        """<feed xmlns="http://www.w3.org/2005/Atom"><title>t</title><entry><title type='html'>x &lt; y</title><link rel="alternate" href="https://ex.com/1"/><updated>2024-10-02T13:00:00Z</updated></entry></feed>""",
        """<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/"><item rdf:about="x"><title>t</title></item></rdf:RDF>""",
        """<a><!-- comment --><b x="1" y='2'>text</b><?pi target?><c/><d>  spaced  </d></a>""",
        """<a>&#233;&#xe9;&#x1F600;&lt;&gt;&quot;&apos;&amp;</a>""",
        "<a>line1\r\nline2\rline3</a>",
        """<a v="tab	here
newline"/>""",
        """<ünï:tag xmlns:ünï="urn:x" ünï:a="1">日本語</ünï:tag>""",
        """<a><![CDATA[ ]] ]> & < ]]><![CDATA[second]]></a>""",
        "<a/>",
        "<a></a>",
        "  \n<a> </a>\n  ",
    )

    @Test fun xmlLiteBuildsTheSameTreeAsTheJdkParser() {
        for (doc in xmlCorpus) {
            assertEquals(canonical(jdkParse(doc)), canonical(XmlLite.parse(doc.trimStart())), "tree for: ${doc.take(70)}")
        }
    }

    private val xmlRejectCorpus = listOf(
        "", "<not xml", "<a><b></a>", "<a>&nbsp;</a>", "<a></b>", "<a/><b/>", "text<a/>", "<a/>tail", "<a x=1/>", "<a x='1' x='2'/>",
        "<a x='<'/>", "<a><![CDATA[oops</a>", "<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><a>&x;</a>", "<!DOCTYPE a><a/>",
        "<a>&#0;</a>", "<a>&#xD800;</a>", "<a>&amp</a>", "<a", "<", "</a>", "<a b/>", "<a b=\"c\"d=\"e\"/>", "<1a/>", "<a><</a>", "<a>&;</a>",
    )

    @Test fun xmlLiteRejectsWhatTheJdkParserRejects() {
        for (doc in xmlRejectCorpus) {
            assertTrue(runCatching { jdkParse(doc) }.isFailure, "test corpus item should be rejected by the JDK too: ${doc.take(60)}")
            assertTrue(runCatching { XmlLite.parse(doc) }.isFailure, "XmlLite should reject: ${doc.take(60)}")
        }
    }

    @Test fun xmlFuzzAgreesWithTheJdkParser() {
        val rnd = Random(2718)
        val alphabet = "<>&/\"'=;!-[]?# abx1\n"
        var bothAccepted = 0
        var bothRejected = 0
        val mismatches = ArrayList<String>()
        repeat(6000) {
            // The XML declaration itself is not validated (XmlLite ignores it), so mutate only what follows it.
            val original = xmlCorpus[rnd.nextInt(xmlCorpus.size)].trimStart()
            val declEnd = if (original.startsWith("<?xml")) original.indexOf("?>") + 2 else 0
            val head = original.substring(0, declEnd)
            val chars = original.substring(declEnd).toMutableList()
            repeat(rnd.nextInt(1, 3)) {
                when (rnd.nextInt(3)) {
                    0 -> if (chars.isNotEmpty()) chars.removeAt(rnd.nextInt(chars.size))
                    1 -> chars.add(rnd.nextInt(chars.size + 1), alphabet[rnd.nextInt(alphabet.length)])
                    else -> if (chars.isNotEmpty()) chars[rnd.nextInt(chars.size)] = alphabet[rnd.nextInt(alphabet.length)]
                }
            }
            val doc = head + chars.joinToString("")
            val jdk = runCatching { jdkParse(doc) }
            val mine = runCatching { XmlLite.parse(doc) }
            if (jdk.isSuccess && mine.isSuccess) {
                if (canonical(jdk.getOrThrow()) != canonical(mine.getOrThrow())) mismatches += "tree differs: ${doc.escaped()}"
                bothAccepted++
            } else if (jdk.isFailure && mine.isFailure) {
                bothRejected++
            } else {
                // The one sanctioned disagreement: names/prefixes the JDK refuses only because of namespace binding.
                val jdkMessage = jdk.exceptionOrNull()?.message.orEmpty()
                val namespaceOnly = jdk.isFailure && ("prefix" in jdkMessage || "namespace" in jdkMessage.lowercase())
                if (!namespaceOnly) mismatches += "accept/reject: ${doc.escaped()} jdk=${jdk.exceptionOrNull()?.message} mine=${mine.exceptionOrNull()?.message}"
            }
        }
        assertTrue(mismatches.isEmpty(), "${mismatches.size} mismatch(es):\n" + mismatches.distinct().take(20).joinToString("\n"))
        assertTrue(bothAccepted > 300 && bothRejected > 300, "fuzz should exercise both outcomes: accepted=$bothAccepted rejected=$bothRejected")
    }

    // ---- helpers ---------------------------------------------------------

    private fun randomString(rnd: Random, min: Int, max: Int): String = buildString {
        repeat(rnd.nextInt(min, max + 1)) {
            when (rnd.nextInt(4)) {
                0 -> append(rnd.nextInt(0x20, 0x7F).toChar())
                1 -> append(rnd.nextInt(0xA0, 0x2FFF).toChar())
                2 -> appendCodePoint(rnd.nextInt(0x1F300, 0x1FAFF))
                else -> append("  +&=%/?#"[rnd.nextInt(9)])
            }
        }
    }

    private fun String.escaped(): String = buildString {
        for (c in this@escaped) if (c.code in 0x20..0x7E) append(c) else append("\\u%04x".format(c.code))
    }
}
