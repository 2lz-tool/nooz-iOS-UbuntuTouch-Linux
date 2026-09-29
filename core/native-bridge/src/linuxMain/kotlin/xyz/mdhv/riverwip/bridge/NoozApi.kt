@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package xyz.mdhv.riverwip.bridge

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName

/*
 * The C surface of libnooz_core.so. Every function is synchronous, takes UTF-8 C strings and returns a
 * UTF-8 JSON string that the caller releases with nooz_free(). Hosts call the slow ones (refresh,
 * add_source, article) from a worker thread. Errors never throw across the boundary: they come back as
 * {"ok":false,"error":"..."}.
 */

private var core: NoozCore? = null
private val json = Json { encodeDefaults = true }

private fun reply(element: JsonElement): CPointer<ByteVar> {
    val bytes = json.encodeToString(JsonElement.serializer(), element).encodeToByteArray()
    val out = nativeHeap.allocArray<ByteVar>(bytes.size + 1)
    for (i in bytes.indices) out[i] = bytes[i]
    out[bytes.size] = 0
    return out
}

private fun error(message: String) = reply(buildJsonObject { put("ok", false); put("error", message) })

private inline fun call(crossinline block: suspend NoozCore.() -> JsonElement): CPointer<ByteVar> {
    val c = core ?: return error("nooz_open has not been called")
    return try {
        reply(runBlocking { c.block() })
    } catch (t: Throwable) {
        error(t.message ?: t::class.simpleName ?: "error")
    }
}

/** Open (creating if needed) the store. Call once, before anything else. */
@CName("nooz_open")
fun noozOpen(dataDir: CPointer<ByteVar>?, cacheDir: CPointer<ByteVar>?, assetsDir: CPointer<ByteVar>?): CPointer<ByteVar> {
    if (dataDir == null || cacheDir == null) return error("data and cache directories are required")
    return try {
        if (core == null) core = NoozCore(LinuxDataPlatform(dataDir.toKString(), cacheDir.toKString(), assetsDir?.toKString().orEmpty()))
        reply(buildJsonObject { put("ok", true) })
    } catch (t: Throwable) {
        error(t.message ?: "could not open the store")
    }
}

/** Release a string returned by any nooz_* function. */
@CName("nooz_free")
fun noozFree(p: CPointer<ByteVar>?) {
    if (p != null) nativeHeap.free(p.rawValue)
}

@CName("nooz_add_source")
fun noozAddSource(url: CPointer<ByteVar>?): CPointer<ByteVar> {
    val u = url?.toKString() ?: return error("url required")
    return call { addSource(u) }
}

@CName("nooz_add_feed")
fun noozAddFeed(url: CPointer<ByteVar>?, title: CPointer<ByteVar>?): CPointer<ByteVar> {
    val u = url?.toKString() ?: return error("url required")
    val t = title?.toKString() ?: u
    return call { addResolvedFeed(u, t) }
}

@CName("nooz_sources")
fun noozSources(): CPointer<ByteVar> = call { sources() }

@CName("nooz_set_source_enabled")
fun noozSetSourceEnabled(id: CPointer<ByteVar>?, enabled: Int): CPointer<ByteVar> {
    val i = id?.toKString() ?: return error("id required")
    return call { setSourceEnabled(i, enabled != 0) }
}

@CName("nooz_remove_source")
fun noozRemoveSource(id: CPointer<ByteVar>?): CPointer<ByteVar> {
    val i = id?.toKString() ?: return error("id required")
    return call { removeSource(i) }
}

@CName("nooz_refresh")
fun noozRefresh(): CPointer<ByteVar> = call { refresh() }

@CName("nooz_items")
fun noozItems(limit: Int): CPointer<ByteVar> = call { items(if (limit <= 0) 200 else limit) }

@CName("nooz_article")
fun noozArticle(id: CPointer<ByteVar>?): CPointer<ByteVar> {
    val i = id?.toKString() ?: return error("id required")
    return call { article(i) }
}

@CName("nooz_mark_read")
fun noozMarkRead(id: CPointer<ByteVar>?): CPointer<ByteVar> {
    val i = id?.toKString() ?: return error("id required")
    return call { markRead(i) }
}
