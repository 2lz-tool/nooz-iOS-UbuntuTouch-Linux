package xyz.mdhv.riverwip.data.mapping

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.mdhv.riverwip.model.Topic
import xyz.mdhv.riverwip.model.TopicEvidence

/**
 * Manual JSON encode/decode for the structured columns Room can't store natively
 * ([TopicEvidence] lists, topic/source count maps). Kept here rather than as Room
 * `@TypeConverter`s so the storage format is explicit and testable on its own, and
 * kept out of `:core:model` so the persistence format never leaks into the domain
 * types — the domain classes carry no serialization annotations.
 */
private val json = Json { ignoreUnknownKeys = true }

object TopicEvidenceJson {
    fun encode(evidence: List<TopicEvidence>): String {
        // Built via explicit JsonElement constructors (not the builder DSL): the
        // DSL's `add`/`put` extensions are shadowed by JsonArrayBuilder/
        // JsonObjectBuilder's own collection-like members under some overload
        // resolutions, which silently demands a JsonElement instead of a raw
        // String. Explicit construction sidesteps that ambiguity entirely.
        val arr = JsonArray(
            evidence.map { e ->
                JsonObject(
                    mapOf(
                        "topic" to JsonPrimitive(e.topic.key),
                        "ruleId" to JsonPrimitive(e.ruleId),
                        "matchedTerms" to JsonArray(e.matchedTerms.map { JsonPrimitive(it) }),
                    ),
                )
            },
        )
        return json.encodeToString(JsonArray.serializer(), arr)
    }

    fun decode(raw: String): List<TopicEvidence> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.parseToJsonElement(raw).jsonArray.map { el ->
                val o = el.jsonObject
                TopicEvidence(
                    topic = Topic.fromKey(o["topic"]?.jsonPrimitive?.content ?: "other"),
                    ruleId = o["ruleId"]?.jsonPrimitive?.content ?: "unknown",
                    matchedTerms = (o["matchedTerms"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

object CountMapJson {
    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    fun encode(map: Map<String, Int>): String = json.encodeToString(serializer, map)

    fun decode(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return try { json.decodeFromString(serializer, raw) } catch (_: Exception) { emptyMap() }
    }
}
