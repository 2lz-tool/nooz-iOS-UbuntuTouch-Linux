package xyz.mdhv.riverwip.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities. Kept separate from the pure domain types in `:core:model` — the
 * mappers in the `mapping` package translate between them, so persistence concerns never
 * leak into the domain/analysis core. Structured domain fields ([TopicEvidence]
 * lists, count maps) are stored as JSON string columns (see `JsonColumns.kt`) so
 * the persistence format is a `:core:data` concern, not a domain-type one.
 */
@Entity(
    tableName = "sources",
    indices = [Index(value = ["url"], unique = false)],
)
data class SourceEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val url: String,
    val title: String,
    val tier: String,
    val enabled: Boolean,
    val addedAt: Long,
    // Per-source health (brief §P6 groundwork; populated by fetch in P2).
    val etag: String? = null,
    val lastModified: String? = null,
    val lastFetchAt: Long? = null,
    val lastError: String? = null,
    val consecutiveFailures: Int = 0,
)

/**
 * An ingested item (brief §4). [topicsJson] is a JSON-encoded `List<TopicEvidence>`
 * (see `JsonColumns.TopicEvidenceJson`) — classification carries its own evidence
 * all the way into storage, so "tap to see why" never depends on recomputation.
 * [id] is stable (derived from [canonicalUrl] — see `Ids.itemId`), so re-ingesting
 * the same article across fetch cycles is naturally idempotent.
 */
@Entity(
    tableName = "items",
    indices = [
        Index(value = ["canonicalUrl"], unique = true),
        Index(value = ["sourceId"]),
        Index(value = ["publishedAt"]),
    ],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val canonicalUrl: String,
    val title: String,
    val author: String?,
    val publishedAt: Long,
    val fetchedAt: Long,
    val summary: String?,
    val fullTextCached: Boolean,
    val topicsJson: String,
    val simhash: Long,
    val imageUrl: String? = null,
    val declaredNsfw: Boolean = false,
)

/**
 * A read event (brief §3/§4): coarse [dwellBucket] only, never a precise
 * duration — data minimalism is enforced at the schema, not just by convention.
 */
@Entity(tableName = "read_events", indices = [Index(value = ["itemId"]), Index(value = ["openedAt"])])
data class ReadEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: String,
    val openedAt: Long,
    val dwellBucket: String,
    val viaRiver: Boolean,
)

/**
 * The tiny, permanent weekly abstraction the river renders (brief §4). Count maps
 * are JSON-encoded (see `JsonColumns.CountMapJson`); one row per period, upserted
 * by [weekStart] as the primary key so recomputation is naturally idempotent.
 */
@Entity(tableName = "weekly_aggregates")
data class WeeklyAggregateEntity(
    @PrimaryKey val weekStart: Long,
    val streamCountsByTopicJson: String,
    val readCountsByTopicJson: String,
    val sourceCountsJson: String,
)

/**
 * A saved clipping (owner's Clippings section). Denormalized snapshot keyed by
 * [itemId] — one clipping per article, so re-saving replaces rather than
 * duplicates, and the clipping outlives the item's retention window.
 */
@Entity(tableName = "clippings", indices = [Index(value = ["savedAt"])])
data class ClippingEntity(
    @PrimaryKey val itemId: String,
    val title: String,
    val sourceId: String,
    val sourceTitle: String?,
    val author: String?,
    val canonicalUrl: String,
    val topicKey: String,
    val publishedAt: Long,
    val savedAt: Long,
    val excerpt: String?,
)

/**
 * The extracted body of an article the reader has actually opened, in an FTS4
 * index (D37).
 *
 * Article prose used to live *only* as loose `.txt` files in [FullTextCache],
 * which has no way to enumerate or query them — so the Stand's search could
 * only ever match a headline, and finding a story by a half-remembered phrase
 * from the middle of it was impossible. This is that same text, indexed.
 *
 * `unicode61`, not the default `simple` tokenizer: `simple` treats every
 * non-ASCII byte as a token separator, which would shred Telugu, Tamil,
 * Devanagari, Gujarati, Gurmukhi and Odia into unsearchable fragments — and the
 * catalogue now carries feeds in all of them.
 *
 * Only bodies live here. Titles and summaries are matched in memory against the
 * items already on screen, because those exist for every item whereas a body
 * exists only once an article has been opened; searching one table would have
 * quietly answered a narrower question than the reader asked.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, contentEntity = Any::class)
@Entity(tableName = "article_text")
data class ArticleTextEntity(
    /**
     * `autoGenerate` is load-bearing, not decoration. FTS4's only key is the
     * implicit rowid, and 0 is a *valid* rowid — so without this, every insert
     * supplied rowid 0, and REPLACE-on-conflict made each newly indexed article
     * overwrite the previous one. The index would have held exactly one row,
     * for the last article opened, while looking entirely healthy.
     */
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val itemId: String,
    val body: String,
)
