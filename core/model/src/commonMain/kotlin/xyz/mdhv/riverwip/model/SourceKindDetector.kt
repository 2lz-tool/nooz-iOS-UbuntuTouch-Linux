package xyz.mdhv.riverwip.model

/**
 * Infer a [SourceKind] from a URL, so add-by-URL routes to the right fetch/parse
 * path. Pure and testable. Defaults to [SourceKind.RSS] — the generic keyed
 * [SourceKind.API] is only ever chosen explicitly via a keyed provider, never
 * guessed.
 */
object SourceKindDetector {
    fun detect(url: String): SourceKind {
        val u = url.lowercase()
        return when {
            "news.google.com" in u -> SourceKind.GOOGLE_NEWS
            "api.gdeltproject.org" in u || "gdeltproject.org/api" in u -> SourceKind.GDELT
            "/api/v1/timelines/" in u -> SourceKind.MASTODON
            "content.guardianapis.com" in u -> SourceKind.GUARDIAN
            else -> SourceKind.RSS
        }
    }
}
