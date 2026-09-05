package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.data.model.ValidationCodeEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class ValidationCodeMapperTest {

    private val mapper = Mappers.getMapper(ValidationCodeMapper::class.java)

    @Test
    fun `toValidationCode - maps all fields`() {
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val entity = entity(
            id = id,
            userId = userId,
            sessionId = sessionId,
            media = ValidationCodeMedia.SMS.name,
            reasons = arrayOf(ValidationCodeReason.PHONE_NUMBER_CLAIM.name),
        )

        val code = mapper.toValidationCode(entity)

        assertEquals(id, code.id)
        assertEquals("123456", code.code)
        assertEquals(userId, code.userId)
        assertEquals(sessionId, code.sessionId)
        assertEquals(ValidationCodeMedia.SMS, code.media)
        assertEquals(listOf(ValidationCodeReason.PHONE_NUMBER_CLAIM), code.reasons)
    }

    @Test
    fun `toValidationCode - throws when media is unknown`() {
        val entity = entity(media = "UNKNOWN")

        val exception = assertThrows<BusinessException> {
            mapper.toValidationCode(entity)
        }
        assertEquals("mapper.validation_code.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toValidationCode - throws when one reason is unknown`() {
        val entity = entity(reasons = arrayOf(ValidationCodeReason.EMAIL_CLAIM.name, "UNKNOWN"))

        val exception = assertThrows<BusinessException> {
            mapper.toValidationCode(entity)
        }
        assertEquals("mapper.validation_code.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    private fun entity(
        id: UUID? = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        sessionId: UUID = UUID.randomUUID(),
        media: String = ValidationCodeMedia.EMAIL.name,
        reasons: Array<String> = arrayOf(ValidationCodeReason.EMAIL_CLAIM.name),
    ): ValidationCodeEntity {
        return ValidationCodeEntity(
            code = "123456",
            userId = userId,
            media = media,
            reasons = reasons,
            sessionId = sessionId,
            resendDate = null,
            expirationDate = LocalDateTime.now().plusMinutes(10),
        ).apply { this.id = id }
    }
}
