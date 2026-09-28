package xyz.mdhv.riverwip.data.mapping

import xyz.mdhv.riverwip.data.db.ItemEntity
import xyz.mdhv.riverwip.model.Item

fun ItemEntity.toDomain(): Item = Item(
    id = id,
    sourceId = sourceId,
    canonicalUrl = canonicalUrl,
    title = title,
    author = author,
    publishedAt = publishedAt,
    fetchedAt = fetchedAt,
    summary = summary,
    fullTextCached = fullTextCached,
    topics = TopicEvidenceJson.decode(topicsJson),
    simhash = simhash,
    imageUrl = imageUrl,
    declaredNsfw = declaredNsfw,
)

fun Item.toEntity(): ItemEntity = ItemEntity(
    id = id,
    sourceId = sourceId,
    canonicalUrl = canonicalUrl,
    title = title,
    author = author,
    publishedAt = publishedAt,
    fetchedAt = fetchedAt,
    summary = summary,
    fullTextCached = fullTextCached,
    topicsJson = TopicEvidenceJson.encode(topics),
    simhash = simhash,
    imageUrl = imageUrl,
    declaredNsfw = declaredNsfw,
)
