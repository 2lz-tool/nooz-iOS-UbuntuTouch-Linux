package xyz.mdhv.riverwip.inference

/**
 * The one inference interface, three providers (brief §5): [Provenance.NATIVE]
 * (on-device) is the default for everything; [Provenance.CLOUD] is possible
 * only via Urbana's explicit routing and must always be visibly marked in the
 * UI — provenance is never hidden or aggregated away.
 *
 * Warm radium = on-device, cold cyan = cloud (the ecosystem's fixed provenance
 * convention — see `Tokens.Color.provenanceNative`/`provenanceCloud` in
 * `:core:design`). Never invert it.
 */
enum class Provenance { NATIVE, CLOUD }

/**
 * A span-rewrite request (brief §P5): the model sees the **full sentence** for
 * context but must replace only [spanText] within it, preserving every fact.
 */
data class RewriteRequest(
    val fullSentence: String,
    val spanText: String,
    val spanStart: Int,
    val spanEnd: Int,
)

sealed interface RewriteResult {
    data class Success(val rewrittenSentence: String, val provenance: Provenance) : RewriteResult
    /** The provider ran but declined or errored — never silent (brief §3). */
    data class Failed(val reason: String) : RewriteResult
}

/** Nooz Flash (owner's #6): today's flowed headlines, nothing else — no article bodies, so a digest can never surface a claim its source didn't put in the headline itself. */
data class DigestRequest(val headlines: List<String>)

sealed interface DigestResult {
    data class Success(val flash: String, val provenance: Provenance) : DigestResult
    /** The provider ran but declined or errored — never silent (brief §3). */
    data class Failed(val reason: String) : DigestResult
}

interface InferenceProvider {
    /** A stable id for logging/ordering, e.g. "urbana", "local-llama", "mlkit". */
    val id: String

    /** Runtime capability check. Absent support must return false, never throw (brief §5). */
    suspend fun isAvailable(): Boolean

    suspend fun rewrite(request: RewriteRequest): RewriteResult

    /** Compress [DigestRequest.headlines] into one flash line (owner's #6: "10 words or less"). */
    suspend fun digest(request: DigestRequest): DigestResult
}
