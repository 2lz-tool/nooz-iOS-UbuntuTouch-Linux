package xyz.mdhv.riverwip.data.net

import xyz.mdhv.riverwip.data.IoDispatcher
import okio.Source

/** One HTTP GET, exactly as sent: redirects are the caller's business (see [HttpClient]). */
class RawRequest(
    val url: String,
    val headers: Map<String, String>,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
)

/**
 * The response to a [RawRequest]. [body] is already decoded if the server sent
 * `Content-Encoding: gzip`; [contentLength] is the *transferred* length or -1.
 * Blocking by design (callers run on `IoDispatcher`); always [close] it.
 */
interface RawResponse {
    val code: Int
    fun header(name: String): String?
    val contentLength: Long

    /** Body of a 2xx/3xx response, or the error body of a 4xx/5xx one (empty if the server sent none). */
    val body: Source
    fun close()
}

/** The one platform primitive of the HTTP stack: open [request] and return once the headers are in. */
expect fun openHttp(request: RawRequest): RawResponse
