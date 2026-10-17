package com.sympauthy.api.mapper.flow

import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import io.micronaut.serde.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.LocalDateTime
import java.util.*

class ClaimsValidationFlowResultResourceMapperTest {

    private val mapper = ClaimsValidationFlowResultResourceMapper()

    private val validationCode = ValidationCode(
        id = UUID.randomUUID(),
        code = "123456",
        userId = UUID.randomUUID(),
        media = ValidationCodeMedia.SMS,
        reasons = listOf(ValidationCodeReason.PHONE_NUMBER_CLAIM),
        sessionId = UUID.randomUUID(),
        creationDate = LocalDateTime.of(2026, 1, 1, 10, 0),
        resendDate = LocalDateTime.of(2026, 1, 1, 10, 1),
        expirationDate = LocalDateTime.of(2026, 1, 1, 10, 15)
    )

    @Test
    fun `toFlowResource - Publishes the media and the reasons by the names they are published under`() {
        val resource = mapper.toFlowResource(validationCode)

        assertEquals("sms", resource.media)
        assertEquals("sms", resource.code?.media)
        assertEquals(listOf("phone_number_claim"), resource.code?.reasons)
    }

    @Test
    fun `toFlowResource - Publishes the media of a redirect by the name it is published under`() {
        val resource = mapper.toFlowResource(ValidationCodeMedia.EMAIL, URI.create("https://example.com/next"))

        assertEquals("email", resource.media)
    }

    @Test
    fun `toResendResultResource - Publishes the media by the name it is published under`() {
        val resource = mapper.toResendResultResource(ValidationCodeMedia.EMAIL, false, null)

        assertEquals("email", resource.media)
    }

    @Test
    fun `toResource - Serializes every property under its snake_case name`() {
        val json = ObjectMapper.getDefault().writeValueAsString(mapper.toResource(validationCode))

        val properties = ObjectMapper.getDefault().readValue(json, Map::class.java)?.keys

        assertEquals(setOf("id", "media", "reasons", "resend_date"), properties)
    }
}
