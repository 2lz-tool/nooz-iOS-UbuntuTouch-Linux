@file:OptIn(ExperimentalForeignApi::class)

package xyz.mdhv.riverwip.data.net

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import okio.Buffer
import okio.IOException
import okio.Source
import platform.posix.RTLD_NOW
import platform.posix.dlopen
import platform.posix.dlsym
import platform.posix.size_t

/**
 * HTTP for Kotlin/Native on Linux (Ubuntu Touch phones, and the native Linux test binary) over the
 * system's libcurl, which every Ubuntu image ships as `libcurl.so.4` and which brings the system's
 * CA store and TLS with it. It is opened with dlopen at run time rather than linked: the build needs
 * no libcurl headers or arm64 sysroot, and a device without it gets a clear error instead of a
 * binary that will not start.
 *
 * The request is performed to completion and the response served from memory (capped), which is what
 * feeds, articles and the small data packs need; it is not a streaming client for multi-gigabyte files.
 */
actual fun openHttp(request: RawRequest): RawResponse = Curl.perform(request)

private typealias EasyInit = CFunction<() -> COpaquePointer?>
private typealias EasyCleanup = CFunction<(COpaquePointer?) -> Unit>
private typealias EasyPerform = CFunction<(COpaquePointer?) -> Int>
private typealias SetoptLong = CFunction<(COpaquePointer?, Int, Long) -> Int>
private typealias SetoptPtr = CFunction<(COpaquePointer?, Int, COpaquePointer?) -> Int>
private typealias GetinfoLong = CFunction<(COpaquePointer?, Int, CPointer<LongVar>?) -> Int>
private typealias SlistAppend = CFunction<(COpaquePointer?, CPointer<ByteVar>?) -> COpaquePointer?>
private typealias SlistFree = CFunction<(COpaquePointer?) -> Unit>
private typealias StrError = CFunction<(Int) -> CPointer<ByteVar>?>
private typealias GlobalInit = CFunction<(Long) -> Int>

// Option numbers from curl.h; they are part of libcurl's stable ABI.
private const val CURLOPT_WRITEDATA = 10001
private const val CURLOPT_URL = 10002
private const val CURLOPT_POSTFIELDS = 10015
private const val CURLOPT_HTTPHEADER = 10023
private const val CURLOPT_HEADERDATA = 10029
private const val CURLOPT_CUSTOMREQUEST = 10036
private const val CURLOPT_POSTFIELDSIZE = 60
private const val CURLOPT_FOLLOWLOCATION = 52
private const val CURLOPT_NOSIGNAL = 99
private const val CURLOPT_TIMEOUT_MS = 155
private const val CURLOPT_CONNECTTIMEOUT_MS = 156
private const val CURLOPT_WRITEFUNCTION = 20011
private const val CURLOPT_HEADERFUNCTION = 20079
private const val CURLOPT_ACCEPT_ENCODING = 10102
private const val CURLINFO_RESPONSE_CODE = 0x200000 + 2
private const val CURL_GLOBAL_DEFAULT = 3L

private const val MAX_BODY_BYTES = 256L * 1024 * 1024

private class Transfer {
    val body = Buffer()
    val headers = LinkedHashMap<String, String>()
    var tooLarge = false
}

private object Curl {
    private val lib: COpaquePointer? by lazy {
        listOf("libcurl.so.4", "libcurl-gnutls.so.4", "libcurl.so").firstNotNullOfOrNull { dlopen(it, RTLD_NOW) }
    }

    private fun <T : CFunction<*>> sym(name: String): CPointer<T> {
        val handle = lib ?: throw IOException("libcurl is not installed on this system (looked for libcurl.so.4)")
        val p = dlsym(handle, name) ?: throw IOException("libcurl is missing $name")
        return p.reinterpret()
    }

    private val easyInit by lazy { sym<EasyInit>("curl_easy_init") }
    private val easyCleanup by lazy { sym<EasyCleanup>("curl_easy_cleanup") }
    private val easyPerform by lazy { sym<EasyPerform>("curl_easy_perform") }
    private val setoptLong by lazy { sym<SetoptLong>("curl_easy_setopt") }
    private val setoptPtr by lazy { sym<SetoptPtr>("curl_easy_setopt") }
    private val getinfo by lazy { sym<GetinfoLong>("curl_easy_getinfo") }
    private val slistAppend by lazy { sym<SlistAppend>("curl_slist_append") }
    private val slistFree by lazy { sym<SlistFree>("curl_slist_free_all") }
    private val strError by lazy { sym<StrError>("curl_easy_strerror") }

    private val initialised by lazy {
        sym<GlobalInit>("curl_global_init").invoke(CURL_GLOBAL_DEFAULT)
        true
    }

    private val onWrite = staticCFunction { data: CPointer<ByteVar>?, size: size_t, nmemb: size_t, user: COpaquePointer? ->
        val transfer = user!!.asStableRef<Transfer>().get()
        val n = (size * nmemb).toLong()
        if (transfer.body.size + n > MAX_BODY_BYTES) {
            transfer.tooLarge = true
            return@staticCFunction 0.convert<size_t>() // returning short aborts the transfer
        }
        transfer.body.write(data!!.readBytes(n.toInt()))
        n.convert<size_t>()
    }

    private val onHeader = staticCFunction { data: CPointer<ByteVar>?, size: size_t, nmemb: size_t, user: COpaquePointer? ->
        val transfer = user!!.asStableRef<Transfer>().get()
        val n = (size * nmemb).toInt()
        val line = data!!.readBytes(n).decodeToString().trimEnd('\r', '\n')
        if (line.startsWith("HTTP/")) {
            transfer.headers.clear() // a redirect or 100-continue precedes the final response's header block
        } else {
            val colon = line.indexOf(':')
            if (colon > 0) transfer.headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        n.convert<size_t>()
    }

    fun perform(request: RawRequest): RawResponse {
        check(initialised)
        val easy = easyInit.invoke() ?: throw IOException("curl_easy_init failed")
        val transfer = Transfer()
        val ref = StableRef.create(transfer)
        var headerList: COpaquePointer? = null
        try {
            fun opt(option: Int, value: Long) = setoptLong.invoke(easy, option, value)
            fun opt(option: Int, value: COpaquePointer?) = setoptPtr.invoke(easy, option, value)

            memScoped {
                // libcurl copies string options, so these need only outlive the setopt calls.
                fun str(s: String): CPointer<ByteVar> {
                    val bytes = s.encodeToByteArray()
                    val p = allocArray<ByteVar>(bytes.size + 1)
                    for (i in bytes.indices) p[i] = bytes[i]
                    p[bytes.size] = 0
                    return p
                }
                opt(CURLOPT_URL, str(request.url))
                opt(CURLOPT_FOLLOWLOCATION, 0L) // HttpClient follows redirects itself, across schemes
                opt(CURLOPT_NOSIGNAL, 1L)
                opt(CURLOPT_CONNECTTIMEOUT_MS, request.connectTimeoutMs.toLong())
                opt(CURLOPT_TIMEOUT_MS, (request.connectTimeoutMs + request.readTimeoutMs).toLong())
                // Let curl do the gzip: it also removes the Content-Encoding header, as the JVM transport does.
                opt(CURLOPT_ACCEPT_ENCODING, str("gzip"))
                if (request.method != "GET") opt(CURLOPT_CUSTOMREQUEST, str(request.method))
                for ((name, value) in request.headers) {
                    if (name.equals("Accept-Encoding", ignoreCase = true)) continue
                    headerList = slistAppend.invoke(headerList, str("$name: $value"))
                }
                opt(CURLOPT_HTTPHEADER, headerList)
                request.body?.let { payload ->
                    val copy = allocArray<ByteVar>(payload.size + 1)
                    for (i in payload.indices) copy[i] = payload[i]
                    opt(CURLOPT_POSTFIELDSIZE, payload.size.toLong())
                    opt(CURLOPT_POSTFIELDS, copy) // not copied: valid until perform returns, which is inside this scope
                }
                opt(CURLOPT_WRITEFUNCTION, onWrite.reinterpret())
                opt(CURLOPT_WRITEDATA, ref.asCPointer())
                opt(CURLOPT_HEADERFUNCTION, onHeader.reinterpret())
                opt(CURLOPT_HEADERDATA, ref.asCPointer())

                val rc = easyPerform.invoke(easy)
                if (rc != 0) {
                    if (transfer.tooLarge) throw IOException("response too large (over ${MAX_BODY_BYTES / (1024 * 1024)} MB)")
                    throw IOException("curl error $rc: ${strError.invoke(rc)?.toKString().orEmpty()}")
                }
                val status = alloc<LongVar>()
                getinfo.invoke(easy, CURLINFO_RESPONSE_CODE, status.ptr)
                return CurlResponse(status.value.toInt(), transfer)
            }
        } finally {
            if (headerList != null) slistFree.invoke(headerList)
            easyCleanup.invoke(easy)
            ref.dispose()
        }
    }
}

private class CurlResponse(override val code: Int, private val transfer: Transfer) : RawResponse {
    override fun header(name: String): String? = transfer.headers[name.lowercase()]
    override val contentLength: Long get() = transfer.headers["content-length"]?.toLongOrNull() ?: -1
    override val body: Source get() = transfer.body
    override fun close() = transfer.body.clear()
}
