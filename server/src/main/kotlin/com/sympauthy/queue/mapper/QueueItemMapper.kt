package com.sympauthy.queue.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.queue.model.QueueItem
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

    private fun invalidProperty(invalidProperty: String): BusinessException = internalBusinessExceptionOf(
        detailsId = "mapper.queue_item.invalid_property",
        values = arrayOf("property" to invalidProperty)
    )
}
