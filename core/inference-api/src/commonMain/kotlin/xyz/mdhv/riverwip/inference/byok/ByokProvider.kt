package xyz.mdhv.riverwip.inference.byok

import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import xyz.mdhv.riverwip.inference.DigestRequest
import xyz.mdhv.riverwip.inference.DigestResult
import xyz.mdhv.riverwip.inference.InferenceProvider
import xyz.mdhv.riverwip.inference.PromptTemplates
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.inference.RewriteRequest
import xyz.mdhv.riverwip.inference.RewriteResult
import okio.buffer
import xyz.mdhv.riverwip.data.IoDispatcher
import xyz.mdhv.riverwip.data.net.RawRequest
import xyz.mdhv.riverwip.data.net.openHttp

/**
 * The bring-your-own-key provider (owner's #18): routes a span rewrite to the
 * user's own OpenAI-compatible chat-completions endpoint. Available only when a
 * key is configured; the result always carries [Provenance.CLOUD] so the UI can
 * mark it (brief §5). The deterministic [xyz.mdhv.riverwip.model.FidelityGuard]
 * still vets the output downstream — a cloud model can't smuggle a fabrication
 * past it.
 */
class ByokProvider(private val store: ByokConfigStore) : InferenceProvider {
    override val id: String = "byok"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun isAvailable(): Boolean = store.isConfigured

    override suspend fun rewrite(request: RewriteRequest): RewriteResult {
        val cfg = store.load()
        if (!cfg.isComplete) return RewriteResult.Failed("No API key configured")
        return when (val r = chatComplete(cfg, PromptTemplates.REWRITE_SYSTEM, PromptTemplates.rewriteUser(request))) {
            is ChatOutcome.Success -> RewriteResult.Success(r.content, Provenance.CLOUD)
            is ChatOutcome.Failed -> RewriteResult.Failed(r.reason)
        }
    }

    override suspend fun digest(request: DigestRequest): DigestResult {
        val cfg = store.load()
        if (!cfg.isComplete) return DigestResult.Failed("No API key configured")
        if (request.headlines.isEmpty()) return DigestResult.Failed("Nothing flowed yet to compress")
        return when (val r = chatComplete(cfg, PromptTemplates.DIGEST_SYSTEM, PromptTemplates.digestUser(request))) {
            is ChatOutcome.Success -> DigestResult.Success(r.content, Provenance.CLOUD)
            is ChatOutcome.Failed -> DigestResult.Failed(r.reason)
        }
    }

    private sealed interface ChatOutcome {
        data class Success(val content: String) : ChatOutcome
        data class Failed(val reason: String) : ChatOutcome
    }

    /** The one HTTP call every capability routes through — same endpoint, same auth, just a different prompt pair. */
    private suspend fun chatComplete(cfg: ByokConfig, systemPrompt: String, userPrompt: String): ChatOutcome =
        withContext(IoDispatcher) {
            val payload = buildJsonObject {
                put("model", cfg.model)
                put("temperature", 0.2)
                put("messages", buildJsonArray {
                    addJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", userPrompt)
                    }
                })
            }

            try {
                val response = openHttp(
                    RawRequest(
                        url = cfg.chatCompletionsUrl,
                        headers = mapOf(
                            "Content-Type" to "application/json",
                            "Authorization" to "Bearer ${cfg.apiKey}",
                        ),
                        connectTimeoutMs = 20_000,
                        readTimeoutMs = 40_000,
                        method = "POST",
                        body = json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(),
                    ),
                )
                try {
                    val code = response.code
                    val text = response.body.buffer().readUtf8()
                    if (code !in 200..299) {
                        val err = text.take(300)
                        return@withContext ChatOutcome.Failed("Your provider returned HTTP $code${if (err.isBlank()) "" else ": $err"}")
                    }
                    val content = parseContent(text)
                    if (content.isNullOrBlank()) {
                        ChatOutcome.Failed("Your provider returned no result")
                    } else {
                        ChatOutcome.Success(content.trim())
                    }
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                ChatOutcome.Failed("Couldn't reach your provider: ${e.message ?: e::class.simpleName}")
            }
        }

    /** Pull choices[0].message.content out of a standard chat-completions response. */
    private fun parseContent(bodyText: String): String? = try {
        json.parseToJsonElement(bodyText)
            .jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}
