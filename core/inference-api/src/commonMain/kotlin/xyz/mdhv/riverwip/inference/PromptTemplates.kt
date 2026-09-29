package xyz.mdhv.riverwip.inference

/**
 * The exact wording both Flash-capable providers — cloud
 * [xyz.mdhv.riverwip.inference.byok.ByokProvider] and on-device
 * [xyz.mdhv.riverwip.inference.local.LocalLlamaProvider] — send a model for
 * the same capability, kept in one place so the two never drift into
 * producing subtly different behavior for what's supposed to be the same
 * request regardless of which provider the router picked.
 */
internal object PromptTemplates {
    const val REWRITE_SYSTEM =
        "You neutralize loaded language in news sentences. Replace only the specified phrase with a plain, " +
            "neutral wording. Never add, drop, or change any fact, number, named entity, or negation. " +
            "Output only the rewritten sentence."

    const val DIGEST_SYSTEM =
        "You compress a list of news headlines into one plain-language sentence of 10 words or fewer, " +
            "capturing only what the headlines themselves state — never infer a connection, cause, or " +
            "outcome the headlines don't already state. Output only that one sentence, nothing else."

    fun rewriteUser(request: RewriteRequest): String =
        "Sentence:\n${request.fullSentence}\n\n" +
            "Rewrite ONLY the phrase \"${request.spanText}\" to remove its charge, keeping every fact, " +
            "number, name, and negation identical. Return the full sentence, nothing else."

    fun digestUser(request: DigestRequest): String =
        "Today's headlines:\n" + request.headlines.joinToString("\n") { "- $it" }
}
