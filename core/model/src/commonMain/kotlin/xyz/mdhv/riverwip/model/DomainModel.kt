package xyz.mdhv.riverwip.model

/**
 * v1 data model (brief §4). Pure domain types — no Room/Android annotations here;
 * the persistence layer (`:core:data`) maps these to entities. Timestamps are
 * epoch milliseconds (UTC); date bucketing happens in analysis against an
 * explicit time zone so behaviour is testable and deterministic.
 */

/** Source kinds (brief §4). `api` is the generic keyed Tier-B shape. */
enum class SourceKind(val key: String) {
    RSS("rss"),
    GOOGLE_NEWS("googlenews"),
    GDELT("gdelt"),
    GUARDIAN("guardian"),
    MASTODON("mastodon"),
    API("api");

    companion object {
        private val byKey = entries.associateBy(SourceKind::key)
        fun fromKey(key: String): SourceKind? = byKey[key.lowercase()]
    }
}

/**
 * Curation tier. A = curated starters, B = keyed catalogue providers, USER =
 * added by hand. The denominator (brief §1) is always the user's enabled set
 * regardless of tier.
 */
enum class Tier(val key: String) {
    A("A"), B("B"), USER("user");

    companion object {
        private val byKey = entries.associateBy(Tier::key)
        fun fromKey(key: String): Tier = byKey[key] ?: USER
    }
}

/**
 * Coarse dwell buckets, never precise timings (data minimalism, brief §3). The
 * only three grains stored for a read.
 */
enum class DwellBucket(val key: String) {
    GLANCE("glance"), PARTIAL("partial"), READ("read");

    companion object {
        private val byKey = entries.associateBy(DwellBucket::key)
        fun fromKey(key: String): DwellBucket = byKey[key.lowercase()] ?: GLANCE
    }
}

/**
 * A declared source. [id] is stable and derived from [kind]+[url] so re-adding
 * the same feed is idempotent (see [SourceId]).
 */
data class Source(
    val id: String,
    val kind: SourceKind,
    val url: String,
    val title: String,
    val tier: Tier,
    val enabled: Boolean = true,
    val addedAt: Long,
)

/**
 * One piece of classification evidence (brief §4): the topic assigned, the rule
 * that fired, and the exact terms it matched. This is what "tap to see why"
 * reveals — classification carries its own evidence, no black boxes (brief §3).
 */
data class TopicEvidence(
    val topic: Topic,
    val ruleId: String,
    val matchedTerms: List<String>,
)

/**
 * An ingested item. [topics] may hold multiple evidences (an item can touch
 * several topics); the dominant topic for stream/read counts is decided by
 * analysis, but every firing is retained for inspectability. [simhash] powers
 * near-duplicate detection alongside [canonicalUrl].
 */
data class Item(
    val id: String,
    val sourceId: String,
    val canonicalUrl: String,
    val title: String,
    val author: String? = null,
    val publishedAt: Long,
    val fetchedAt: Long,
    val summary: String? = null,
    val fullTextCached: Boolean = false,
    val topics: List<TopicEvidence> = emptyList(),
    val simhash: Long = 0L,
    /** The feed's own image for this item, if it supplied one — see [FeedParser]. Null means the feed had none. */
    val imageUrl: String? = null,
    /**
     * True only when the *source's own feed* declared this item adult/explicit
     * — Media RSS's `<media:rating>` or the podcast `<itunes:explicit>` tag
     * (see [FeedParser]). Never inferred; a source that declares nothing
     * always reads false, per those specs' own default.
     */
    val declaredNsfw: Boolean = false,
)

/**
 * A read event. Stores only a coarse [dwellBucket], never a precise duration
 * (brief §3). [viaRiver] records whether the open came from the river surface.
 */
data class ReadEvent(
    val itemId: String,
    val openedAt: Long,
    val dwellBucket: DwellBucket,
    val viaRiver: Boolean,
)

/**
 * The tiny, permanent weekly abstraction the river renders (brief §4). Counts are
 * keyed by [Topic.key]. [sourceCounts] is per-source stream volume. Kept
 * indefinitely; `Item` rows are pruned but these survive.
 */
data class WeeklyAggregate(
    val weekStart: Long,
    val streamCountsByTopic: Map<String, Int>,
    val readCountsByTopic: Map<String, Int>,
    val sourceCounts: Map<String, Int>,
)
