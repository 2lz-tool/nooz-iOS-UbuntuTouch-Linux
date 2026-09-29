package xyz.mdhv.riverwip.data.net

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.buffer
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the libcurl transport against a local server. CI starts `tools/native/testserver.py` and
 * exports its port as NOOZ_TEST_HTTP_PORT; without it (a plain local run) the tests do nothing.
 */
@OptIn(ExperimentalForeignApi::class)
class OpenHttpLinuxTest {
    private val port: String? = getenv("NOOZ_TEST_HTTP_PORT")?.toKString()

    private fun get(path: String, headers: Map<String, String> = emptyMap(), method: String = "GET", body: ByteArray? = null) =
        openHttp(RawRequest("http://127.0.0.1:$port$path", headers, 5_000, 5_000, method, body))

    @Test fun getsABody() {
        if (port == null) return
        val r = get("/hello")
        assertEquals(200, r.code)
        assertEquals("hello native", r.body.buffer().readUtf8())
        assertEquals("text/plain", r.header("Content-Type"))
        r.close()
    }

    @Test fun gzipIsDecodedEvenWhenTheCallerAsksForIt() {
        if (port == null) return
        val r = get("/gzip", mapOf("Accept-Encoding" to "gzip"))
        assertEquals("compressed text", r.body.buffer().readUtf8())
        r.close()
    }

    @Test fun redirectsAreReportedNotFollowed() {
        if (port == null) return
        val r = get("/redirect")
        assertEquals(302, r.code)
        assertTrue(r.header("Location")!!.endsWith("/hello"))
        r.close()
    }

    @Test fun errorStatusesStillReturnTheirBody() {
        if (port == null) return
        val r = get("/missing")
        assertEquals(404, r.code)
        assertEquals("nope", r.body.buffer().readUtf8())
        r.close()
    }

    @Test fun postsABodyAndCustomHeaders() {
        if (port == null) return
        val r = get("/echo", mapOf("X-Test" to "yes"), "POST", """{"a":1}""".encodeToByteArray())
        assertEquals("POST|yes|{\"a\":1}", r.body.buffer().readUtf8())
        r.close()
    }

    @Test fun connectionFailureIsAnIoError() {
        if (port == null) return
        val failed = try {
            openHttp(RawRequest("http://127.0.0.1:1/", emptyMap(), 1_000, 1_000)); false
        } catch (_: okio.IOException) { true }
        assertTrue(failed)
    }
}
