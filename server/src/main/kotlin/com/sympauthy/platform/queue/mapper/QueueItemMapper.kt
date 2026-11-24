package com.sympauthy.platform.queue.mapper

import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.platform.exception.PlatformException
import com.sympauthy.platform.exception.platformExceptionOf
import com.sympauthy.platform.queue.model.QueueItem
import jakarta.inject.Singleton

@Singleton
class QueueItemMapper {

    /**
     * The item [entity] describes.
     *
     * Throws `mapper.queue_item.invalid_property`, internal, where the row carries no id.
     */
    fun toQueueItem(entity: QueueItemEntity): QueueItem = QueueItem(
        id = entity.id ?: throw invalidProperty("id"),
        destination = entity.destination,
        kind = entity.kind,
        payload = entity.payload,
        creationDate = entity.creationDate,
        expirationDate = entity.expirationDate,
        attemptCount = entity.attemptCount
    )

    private fun invalidProperty(invalidProperty: String): PlatformException = platformExceptionOf(
        detailsId = "mapper.queue_item.invalid_property",
        "property" to invalidProperty
    )
}
