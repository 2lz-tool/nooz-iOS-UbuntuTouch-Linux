package xyz.mdhv.riverwip.data.mapping

import xyz.mdhv.riverwip.data.db.WeeklyAggregateEntity
import xyz.mdhv.riverwip.model.WeeklyAggregate

fun WeeklyAggregateEntity.toDomain(): WeeklyAggregate = WeeklyAggregate(
    weekStart = weekStart,
    streamCountsByTopic = CountMapJson.decode(streamCountsByTopicJson),
    readCountsByTopic = CountMapJson.decode(readCountsByTopicJson),
    sourceCounts = CountMapJson.decode(sourceCountsJson),
)

fun WeeklyAggregate.toEntity(): WeeklyAggregateEntity = WeeklyAggregateEntity(
    weekStart = weekStart,
    streamCountsByTopicJson = CountMapJson.encode(streamCountsByTopic),
    readCountsByTopicJson = CountMapJson.encode(readCountsByTopic),
    sourceCountsJson = CountMapJson.encode(sourceCounts),
)
