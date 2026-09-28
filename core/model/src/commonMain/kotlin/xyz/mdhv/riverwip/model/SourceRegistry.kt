package xyz.mdhv.riverwip.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Source registry / provider-catalogue schema.
 *
 * This mirrors the **provider-catalogue** `services[]` shape (brief §P1/§P6) so
 * the app can consume `catalogue.json` at runtime in P6 with **no migration**.
 * The built-in starters (`Starters`) are expressed in exactly this schema; the
 * remote catalogue simply replaces/augments them. All optional fields are
 * nullable and parsing uses `ignoreUnknownKeys` (see `:core:data`), so the
 * catalogue can add fields without breaking older app versions.
 *
 * `consumedAt: runtime` — nothing here is baked into the build except the seed.
 */
@Serializable
data class Catalogue(
    val version: Int = 1,
    val generatedAt: String? = null,
    val services: List<ServiceDef> = emptyList(),
)

@Serializable
data class ServiceDef(
    val id: String,
    /** Maps to [SourceKind.key]: rss | googlenews | gdelt | guardian | mastodon | api. */
    val kind: String,
    val title: String,
    /** A | B | user (see [Tier]). */
    val tier: String = "A",
    /** "india" | "global" | region hint for balanced seeding. */
    val region: String? = null,
    val homepage: String? = null,
    @SerialName("docsUrl") val docsUrl: String? = null,
    /** True for keyed providers (Guardian Open Platform, Tier B). Honestly labeled in UI. */
    val requiresKey: Boolean = false,
    val keySignupUrl: String? = null,
    val freeTier: FreeTier? = null,
    /** Concrete feed URL for ready-to-add starters. */
    val url: String? = null,
    /** Template for builder kinds (googlenews/gdelt/mastodon), for docs/preview. */
    val urlTemplate: String? = null,
    val example: String? = null,
    val notes: String? = null,
    /** Build-time liveness stamp (brief §0: verify feeds live). ISO-8601 date. */
    val verifiedAt: String? = null,
    val enabledByDefault: Boolean = false,
) {
    val sourceKind: SourceKind? get() = SourceKind.fromKey(kind)
    val tierEnum: Tier get() = Tier.fromKey(tier)
}

/** Free-tier limits, kept so P6's health monitor can surface them locally. */
@Serializable
data class FreeTier(
    val requestsPerDay: Int? = null,
    val requestsPerMonth: Int? = null,
    val rateLimitPerSecond: Double? = null,
    val notes: String? = null,
)

/**
 * Turn a catalogue entry with a concrete [ServiceDef.url] into an addable
 * [Source]. Builder kinds without a concrete URL return null (they go through the
 * builder UI instead).
 */
fun ServiceDef.toSourceOrNull(addedAt: Long): Source? {
    val kindEnum = sourceKind ?: return null
    val u = url ?: return null
    return Source(
        id = Ids.sourceId(kindEnum, u),
        kind = kindEnum,
        url = u,
        title = title,
        tier = tierEnum,
        enabled = enabledByDefault,
        addedAt = addedAt,
    )
}
