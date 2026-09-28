package xyz.mdhv.riverwip.data.mapping

import xyz.mdhv.riverwip.data.db.SourceEntity
import xyz.mdhv.riverwip.model.Source
import xyz.mdhv.riverwip.model.SourceKind
import xyz.mdhv.riverwip.model.Tier

fun SourceEntity.toDomain(): Source = Source(
    id = id,
    kind = SourceKind.fromKey(kind) ?: SourceKind.RSS,
    url = url,
    title = title,
    tier = Tier.fromKey(tier),
    enabled = enabled,
    addedAt = addedAt,
)

fun Source.toEntity(): SourceEntity = SourceEntity(
    id = id,
    kind = kind.key,
    url = url,
    title = title,
    tier = tier.key,
    enabled = enabled,
    addedAt = addedAt,
)
