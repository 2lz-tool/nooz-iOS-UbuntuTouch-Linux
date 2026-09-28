package xyz.mdhv.riverwip.model

/**
 * A saved article — the owner's "Clippings": a Nooz-paper clipping the reader
 * chose to keep. Denormalized on purpose (its own copy of title/source/author/
 * url/topic), so a clipping survives even after the item ages out of the feed's
 * retention window. [savedAt] orders the clippings shelf, newest first.
 */
data class Clipping(
    val itemId: String,
    val title: String,
    val sourceTitle: String?,
    val author: String?,
    val url: String,
    val topicKey: String,
    val publishedAt: Long,
    val savedAt: Long,
    val excerpt: String?,
) {
    val topic: Topic get() = Topic.fromKey(topicKey)
}
