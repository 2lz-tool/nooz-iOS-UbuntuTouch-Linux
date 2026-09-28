package xyz.mdhv.riverwip.data.mapping

import xyz.mdhv.riverwip.data.db.ReadEventEntity
import xyz.mdhv.riverwip.model.DwellBucket
import xyz.mdhv.riverwip.model.ReadEvent

fun ReadEventEntity.toDomain(): ReadEvent = ReadEvent(
    itemId = itemId,
    openedAt = openedAt,
    dwellBucket = DwellBucket.fromKey(dwellBucket),
    viaRiver = viaRiver,
)

fun ReadEvent.toEntity(): ReadEventEntity = ReadEventEntity(
    itemId = itemId,
    openedAt = openedAt,
    dwellBucket = dwellBucket.key,
    viaRiver = viaRiver,
)
