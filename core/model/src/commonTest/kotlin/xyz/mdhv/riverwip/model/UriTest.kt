package xyz.mdhv.riverwip.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UriTest {

    private val base = Uri.parse("http://a/b/c/d;p?q")

    /** RFC 3986 §5.4.1 "normal examples" — the standard's own table is the oracle. */
    @Test fun resolvesRfc3986NormalExamples() {
        val table = mapOf(
            "g:h" to "g:h",
            "g" to "http://a/b/c/g",
            "./g" to "http://a/b/c/g",
            "g/" to "http://a/b/c/g/",
            "/g" to "http://a/g",
            "//g" to "http://g",
            "?y" to "http://a/b/c/d;p?y",
            "g?y" to "http://a/b/c/g?y",
            "#s" to "http://a/b/c/d;p?q#s",
            "g#s" to "http://a/b/c/g#s",
            "g?y#s" to "http://a/b/c/g?y#s",
            ";x" to "http://a/b/c/;x",
            "g;x" to "http://a/b/c/g;x",
            "g;x?y#s" to "http://a/b/c/g;x?y#s",
            "" to "http://a/b/c/d;p?q",
            "." to "http://a/b/c/",
            "./" to "http://a/b/c/",
            ".." to "http://a/b/",
            "../" to "http://a/b/",
            "../g" to "http://a/b/g",
            "../.." to "http://a/",
            "../../" to "http://a/",
            "../../g" to "http://a/g",
        )
        for ((ref, expected) in table) assertEquals(expected, base.resolve(ref), "resolve(\"$ref\")")
    }

    /** RFC 3986 §5.4.2 "abnormal examples". */
    @Test fun resolvesRfc3986AbnormalExamples() {
        val table = mapOf(
            "../../../g" to "http://a/g",
            "../../../../g" to "http://a/g",
            "/./g" to "http://a/g",
            "/../g" to "http://a/g",
            "g." to "http://a/b/c/g.",
            ".g" to "http://a/b/c/.g",
            "g.." to "http://a/b/c/g..",
            "..g" to "http://a/b/c/..g",
            "./../g" to "http://a/b/g",
            "./g/." to "http://a/b/c/g/",
            "g/./h" to "http://a/b/c/g/h",
            "g/../h" to "http://a/b/c/h",
            "g;x=1/./y" to "http://a/b/c/g;x=1/y",
            "g;x=1/../y" to "http://a/b/c/y",
            "g?y/./x" to "http://a/b/c/g?y/./x",
            "g?y/../x" to "http://a/b/c/g?y/../x",
            "g#s/./x" to "http://a/b/c/g#s/./x",
            "g#s/../x" to "http://a/b/c/g#s/../x",
        )
        for ((ref, expected) in table) assertEquals(expected, base.resolve(ref), "resolve(\"$ref\")")
    }

    @Test fun parsesComponents() {
        val u = Uri.parse("https://user@Example.COM:8443/a/b?x=1&y=2#frag")
        assertEquals("https", u.scheme)
        assertEquals("Example.COM", u.host)
        assertEquals(8443, u.port)
        assertEquals("/a/b", u.rawPath)
        assertEquals("x=1&y=2", u.rawQuery)
        assertEquals("frag", u.rawFragment)
        val bare = Uri.parse("https://example.com")
        assertEquals(-1, bare.port)
        assertEquals("", bare.rawPath)
        assertNull(bare.rawQuery)
    }

    @Test fun hostIsNullForRegistryStyleAuthorities() {
        // Same as java.net.URI: not a valid server-based host, so no host — callers fall back.
        assertNull(Uri.parse("https://my_host.example/x").host)
        assertNull(Uri.parse("https://münchen.de/x").host)
        assertNull(Uri.parse("https://example.com:abc/x").host)
        assertEquals("[::1]", Uri.parse("https://[::1]:8080/x").host)
        assertEquals(8080, Uri.parse("https://[::1]:8080/x").port)
        assertEquals("192.168.0.1", Uri.parse("http://192.168.0.1/").host)
    }

    @Test fun rejectsMalformedInput() {
        for (bad in listOf("https://example.com/a b", "https://example.com/a%zz", "https://example.com/a[0]", ":nothing", "1abc:foo", "https://exa mple.com", "http://a/b\\c", "http://a/<x>")) {
            assertFailsWith<IllegalArgumentException>("should reject: $bad") { Uri.parse(bad) }
        }
    }

    @Test fun acceptsBracketsInQueryAndPercentEscapes() {
        assertEquals("q[]=1", Uri.parse("https://example.com/?q[]=1").rawQuery)
        assertEquals("/a%20b", Uri.parse("https://example.com/a%20b").rawPath)
    }

    @Test fun opaqueUrisAreReturnedAsIs() {
        val mail = Uri.parse("mailto:a@b.c")
        assertTrue(mail.isOpaque)
        assertEquals("mailto:a@b.c", base.resolve("mailto:a@b.c"))
    }
}

class UrlEncodingTest {
    @Test fun encodesLikeUrlEncoderWithSpaceAsPercent20() {
        assertEquals("hello%20world", UrlEncoding.encode("hello world"))
        assertEquals("a%2Bb", UrlEncoding.encode("a+b"))
        assertEquals("%C3%A9", UrlEncoding.encode("é"))
        assertEquals("%F0%9F%98%80", UrlEncoding.encode("😀"))
        assertEquals("AZaz09.-*_", UrlEncoding.encode("AZaz09.-*_"))
        assertEquals("%7E", UrlEncoding.encode("~"))
        assertEquals("%22flood%20india%22%20AND%20%28a%20OR%20b%29", UrlEncoding.encode("\"flood india\" AND (a OR b)"))
    }
}
