package xyz.mdhv.riverwip.data.net

import xyz.mdhv.riverwip.data.IoDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use
import xyz.mdhv.riverwip.model.Urls

/**
 * Minimal HTTP GET on top of one platform primitive ([openHttp]) -- zero
 * third-party dependencies, so it is `foss`-clean (no OkHttp/Play). Handles
 * conditional requests (ETag / Last-Modified -> 304) and redirects across
 * schemes (which the JVM's connection class won't follow on its own).
 *
 * Everything the user fetches goes to the user's own chosen sources and nowhere
 * else (brief §0). No cookies, no persistent identifiers.
 */
class HttpClient(
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
    private val maxRedirects: Int = 5,
    private val maxBodyBytes: Int = 5 * 1024 * 1024,
) {
    data class Response(
        val code: Int,
        val body: String,
        val etag: String?,
        val lastModified: String?,
        val contentType: String?,
        val finalUrl: String,
    ) {
        val notModified: Boolean get() = code == 304
        val isSuccess: Boolean get() = code in 200..299
    }

    suspend fun get(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
    ): Response = withContext(IoDispatcher) {
        val headers = buildMap {
            put("User-Agent", userAgent)
            put("Accept-Encoding", "gzip")
            put(
                "Accept",
                "application/rss+xml, application/atom+xml, application/xml, text/xml, application/json, text/html;q=0.8, */*;q=0.5",
            )
            if (etag != null) put("If-None-Match", etag)
            if (lastModified != null) put("If-Modified-Since", lastModified)
        }
        var current = url
        var redirects = 0
        while (true) {
            val resp = openHttp(RawRequest(current, headers, connectTimeoutMs, readTimeoutMs))
            try {
                val code = resp.code
                if (code in REDIRECTS && redirects < maxRedirects) {
                    val loc = resp.header("Location") ?: throw IOException("redirect without Location")
                    current = Urls.resolve(current, loc)
                    redirects++
                    continue
                }
                val body = if (code == 304) "" else readCapped(resp)
                return@withContext Response(
                    code = code,
                    body = body,
                    etag = resp.header("ETag"),
                    lastModified = resp.header("Last-Modified"),
                    contentType = resp.header("Content-Type"),
                    finalUrl = current,
                )
            } finally {
                resp.close()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    /** Reads at most [maxBodyBytes] and decodes as UTF-8; anything beyond the cap is dropped, as before. */
    private fun readCapped(resp: RawResponse): String =
        resp.body.buffer().use { src ->
            val out = okio.Buffer()
            var total = 0L
            val chunk = okio.Buffer()
            while (true) {
                val n = src.read(chunk, 8 * 1024L)
                if (n < 0) break
                total += n
                if (total > maxBodyBytes) break
                out.write(chunk, n)
            }
            out.readUtf8()
        }

    /** [url]'s body as bytes (redirects followed, at most [maxBytes]), or null on any non-2xx status, oversize body or I/O failure. */
    suspend fun getBytes(url: String, maxBytes: Int = 8 * 1024 * 1024): ByteArray? = withContext(IoDispatcher) {
        val headers = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept-Encoding" to "gzip",
            "Accept" to "image/*,*/*;q=0.5",
        )
        try {
            var current = url
            var redirects = 0
            while (true) {
                val resp = openHttp(RawRequest(current, headers, connectTimeoutMs, readTimeoutMs))
                try {
                    if (resp.code in REDIRECTS && redirects < maxRedirects) {
                        current = Urls.resolve(current, resp.header("Location") ?: return@withContext null)
                        redirects++
                        continue
                    }
                    if (resp.code !in 200..299) return@withContext null
                    val out = okio.Buffer()
                    resp.body.buffer().use { src ->
                        val chunk = okio.Buffer()
                        var total = 0L
                        while (true) {
                            val n = src.read(chunk, 16 * 1024L)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) return@withContext null
                            out.write(chunk, n)
                        }
                    }
                    return@withContext out.readByteArray()
                } finally {
                    resp.close()
                }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stream [url] to [dest], following redirects, reporting `(bytesRead, totalBytes)`
     * (total is -1 when unknown). Throws on a non-2xx status. Used for the large
     * one-off downloads (dictionaries, translation packs, models); [acceptGzip]
     * is off for files that are served as-is and must not be transparently re-encoded.
     */
    suspend fun download(
        url: String,
        dest: Path,
        fileSystem: FileSystem,
        what: String,
        acceptGzip: Boolean = false,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) = withContext(IoDispatcher) {
        val headers = buildMap {
            put("User-Agent", DEFAULT_USER_AGENT)
            if (acceptGzip) put("Accept-Encoding", "gzip")
        }
        var current = url
        var redirects = 0
        while (true) {
            val resp = openHttp(RawRequest(current, headers, 20_000, 60_000))
            try {
                if (resp.code in REDIRECTS && redirects < maxRedirects) {
                    val loc = resp.header("Location") ?: error("redirect without Location")
                    current = Urls.resolve(current, loc)
                    redirects++
                    continue
                }
                if (resp.code !in 200..299) error("HTTP ${resp.code} downloading $what")
                val total = resp.contentLength
                var read = 0L
                resp.body.buffer().use { src ->
                    fileSystem.sink(dest).buffer().use { sink ->
                        val chunk = okio.Buffer()
                        while (true) {
                            val n = src.read(chunk, 64 * 1024L)
                            if (n < 0) break
                            sink.write(chunk, n)
                            read += n
                            onProgress(read, total)
                        }
                    }
                }
                return@withContext
            } finally {
                resp.close()
            }
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT = "river/0.1 (+news-omission-reader)"
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}
