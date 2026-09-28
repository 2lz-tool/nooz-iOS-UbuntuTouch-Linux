package xyz.mdhv.riverwip.data.mapping

import xyz.mdhv.riverwip.data.db.ClippingEntity
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.Clipping
import xyz.mdhv.riverwip.model.Item

fun ClippingEntity.toClipping(): Clipping = Clipping(
    itemId = itemId,
    title = title,
    sourceTitle = sourceTitle,
    author = author,
    url = canonicalUrl,
    topicKey = topicKey,
    publishedAt = publishedAt,
    savedAt = savedAt,
    excerpt = excerpt,
)

/** Snapshot an item into a clipping. The dominant topic is resolved once, at save time. */
fun Item.toClippingEntity(sourceTitle: String?, savedAt: Long): ClippingEntity = ClippingEntity(
    itemId = id,
    title = title,
    sourceId = sourceId,
    sourceTitle = sourceTitle,
    author = author,
    canonicalUrl = canonicalUrl,
    topicKey = Classifier.dominantTopic(topics).key,
    publishedAt = publishedAt,
    savedAt = savedAt,
    excerpt = summary,
)
