package xyz.mdhv.riverwip.data.net

import okio.Source
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Android and desktop JVM share this: plain `HttpURLConnection`, zero third-party
 * dependencies, so the `foss` flavour stays free of OkHttp and friends.
 */
actual fun openHttp(request: RawRequest): RawResponse {
    val conn = (URL(request.url).openConnection() as HttpURLConnection).apply {
        requestMethod = request.method
        instanceFollowRedirects = false // HttpURLConnection won't cross schemes anyway; HttpClient follows them
        connectTimeout = request.connectTimeoutMs
        readTimeout = request.readTimeoutMs
        for ((k, v) in request.headers) setRequestProperty(k, v)
        if (request.body != null) doOutput = true
    }
    return try {
        request.body?.let { payload -> conn.outputStream.use { it.write(payload) } }
        val code = conn.responseCode
        Jvm(conn, code)
    } catch (t: Throwable) {
        conn.disconnect()
        throw t
    }
}

private class Jvm(private val conn: HttpURLConnection, override val code: Int) : RawResponse {
    override fun header(name: String): String? = conn.getHeaderField(name)
    override val contentLength: Long get() = conn.contentLengthLong

    override val body: Source by lazy {
        val raw = if (code in 200..399) conn.inputStream else conn.errorStream
        when {
            raw == null -> ByteArrayInputStream(ByteArray(0)).source()
            conn.contentEncoding?.contains("gzip", ignoreCase = true) == true -> GZIPInputStream(raw).source()
            else -> raw.source()
        }
    }

    override fun close() = conn.disconnect()
}
