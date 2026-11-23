package com.sympauthy.mail.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.mail.model.Mail
import com.sympauthy.mail.model.QueuedMail
import com.sympauthy.queue.model.QueueItem
import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*

class QueuedMailMapperTest {

    private val mapper = QueuedMailMapper(ObjectMapper.getDefault())

    private val queuedMail = QueuedMail(
        mail = Mail.CLAIM_VALIDATION,
        receiver = "person@example.com",
        locale = Locale.US,
        parameters = mapOf("code" to "123456")
    )

    @Test
    fun `toQueuedMail - Reads back the mail its payload was written from`() {
        val item = item(kind = Mail.CLAIM_VALIDATION.kind, payload = mapper.toPayload(queuedMail))

        assertEquals(queuedMail, mapper.toQueuedMail(item))
    }

    @Test
    fun `toQueuedMail - Refuses a kind this build sends no mail of`() {
        val item = item(kind = "retired_mail", payload = mapper.toPayload(queuedMail))

        val exception = assertThrows<BusinessException> { mapper.toQueuedMail(item) }

        assertEquals("mapper.queued_mail.invalid_property", exception.detailsId)
        assertEquals("kind", exception.values["property"])
    }

    @Test
    fun `toQueuedMail - Refuses a payload that does not read as a mail's`() {
        val item = item(kind = Mail.CLAIM_VALIDATION.kind, payload = """{"parameters":{}}""")

        val exception = assertThrows<BusinessException> { mapper.toQueuedMail(item) }

        assertEquals("mapper.queued_mail.invalid_property", exception.detailsId)
        assertEquals("payload", exception.values["property"])
    }

    private fun item(kind: String, payload: String) = QueueItem(
        id = UUID.randomUUID(),
        destination = "mail",
        kind = kind,
        payload = payload,
        creationDate = LocalDateTime.of(2026, 1, 1, 12, 0, 0),
        expirationDate = null,
        attemptCount = 0
    )
}
