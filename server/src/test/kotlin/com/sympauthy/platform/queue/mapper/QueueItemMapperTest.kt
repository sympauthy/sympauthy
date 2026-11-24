package com.sympauthy.platform.queue.mapper

import com.sympauthy.data.model.QueueItemEntity
import com.sympauthy.platform.exception.PlatformException
import com.sympauthy.platform.queue.model.QueueItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

class QueueItemMapperTest {

    private val mapper = QueueItemMapper()

    private val now = LocalDateTime.of(2026, 1, 1, 12, 0, 0)

    @Test
    fun `toQueueItem - Carries every column the delivery reads`() {
        val id = UUID.randomUUID()
        val entity = entity(attemptCount = 2).apply { this.id = id }

        assertEquals(
            QueueItem(
                id = id,
                destination = "mail",
                kind = "claim_validation",
                payload = "{}",
                creationDate = now,
                expirationDate = now.plusMinutes(10),
                attemptCount = 2
            ),
            mapper.toQueueItem(entity)
        )
    }

    @Test
    fun `toQueueItem - Refuses a row carrying no id`() {
        val exception = assertThrows<PlatformException> { mapper.toQueueItem(entity()) }

        assertEquals("mapper.queue_item.invalid_property", exception.detailsId)
        assertEquals("id", exception.values["property"])
    }

    private fun entity(attemptCount: Int = 0) = QueueItemEntity(
        destination = "mail",
        kind = "claim_validation",
        payload = "{}",
        creationDate = now,
        expirationDate = now.plusMinutes(10),
        attemptCount = attemptCount
    )
}
