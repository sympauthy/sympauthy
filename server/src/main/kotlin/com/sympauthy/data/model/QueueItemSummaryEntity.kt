package com.sympauthy.data.model

import io.micronaut.core.annotation.Introspected
import java.time.LocalDateTime

/**
 * How many items of one [kind] wait to be delivered to one [destination], and since when the oldest of them has.
 */
@Introspected
data class QueueItemSummaryEntity(
    val destination: String,
    val kind: String,
    val pendingCount: Long,
    val oldestCreationDate: LocalDateTime
)
