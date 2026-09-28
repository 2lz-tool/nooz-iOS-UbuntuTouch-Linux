package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalUrlTest {

    @Test fun stripsTrackingParamsAndFragment() {
        val a = CanonicalUrl.canonicalize("https://ex.com/story?utm_source=x&id=42&fbclid=zz#top")
        assertEquals("https://ex.com/story?id=42", a)
    }

    @Test fun sortsRemainingParamsForStability() {
        val a = CanonicalUrl.canonicalize("https://ex.com/p?b=2&a=1")
        val b = CanonicalUrl.canonicalize("https://ex.com/p?a=1&b=2")
        assertEquals(a, b)
    }

    @Test fun lowercasesSchemeAndHostNotPath() {
        val a = CanonicalUrl.canonicalize("HTTPS://Example.COM/Path/To")
        assertEquals("https://example.com/Path/To", a)
    }

    @Test fun dropsTrailingSlashOnNonRoot() {
        assertEquals("https://ex.com/a/b", CanonicalUrl.canonicalize("https://ex.com/a/b/"))
        // Root slash is preserved-ish (empty path acceptable).
        assertTrue(CanonicalUrl.canonicalize("https://ex.com/").startsWith("https://ex.com"))
    }

    @Test fun addsSchemeWhenMissing() {
        assertEquals("https://ex.com/a", CanonicalUrl.canonicalize("ex.com/a"))
    }

    @Test fun dropsDefaultPort() {
        assertEquals("https://ex.com/a", CanonicalUrl.canonicalize("https://ex.com:443/a"))
        assertEquals("http://ex.com/a", CanonicalUrl.canonicalize("http://ex.com:80/a"))
    }

    @Test fun distinctArticlesStayDistinct() {
        val a = CanonicalUrl.canonicalize("https://ex.com/story/1")
        val b = CanonicalUrl.canonicalize("https://ex.com/story/2")
        assertNotEquals(a, b)
    }

    @Test fun idempotent() {
        val once = CanonicalUrl.canonicalize("https://ex.com/x?utm_medium=a&z=1")
        val twice = CanonicalUrl.canonicalize(once)
        assertEquals(once, twice)
    }
}
