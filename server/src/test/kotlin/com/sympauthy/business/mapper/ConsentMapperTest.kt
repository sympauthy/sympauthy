package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.oauth2.ConsentRevokedBy
import com.sympauthy.data.model.ConsentEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class ConsentMapperTest {

    private val mapper = Mappers.getMapper(ConsentMapper::class.java)

    @Test
    fun `toConsent - maps all fields`() {
        val id = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val revokedById = UUID.randomUUID()
        val consentedAt = LocalDateTime.now().minusHours(1)
        val revokedAt = LocalDateTime.now().minusMinutes(1)
        val entity = entity(
            id = id,
            userId = userId,
            scopes = arrayOf("openid", "profile"),
            consentedAt = consentedAt,
            revokedAt = revokedAt,
            revokedBy = ConsentRevokedBy.ADMIN.name,
            revokedById = revokedById,
        )

        val consent = mapper.toConsent(entity)

        assertEquals(id, consent.id)
        assertEquals(userId, consent.userId)
        assertEquals("audience", consent.audienceId)
        assertEquals("client", consent.promptedByClientId)
        assertEquals(listOf("openid", "profile"), consent.scopes)
        assertEquals(consentedAt, consent.consentedAt)
        assertEquals(revokedAt, consent.revokedAt)
        assertEquals(ConsentRevokedBy.ADMIN, consent.revokedBy)
        assertEquals(revokedById, consent.revokedById)
    }

    @Test
    fun `toConsent - maps an active consent with no revocation`() {
        val consent = mapper.toConsent(entity())

        assertNull(consent.revokedAt)
        assertNull(consent.revokedBy)
        assertNull(consent.revokedById)
    }

    @Test
    fun `toConsent - throws when revokedBy is unknown`() {
        val entity = entity(
            revokedAt = LocalDateTime.now().minusMinutes(1),
            revokedBy = "UNKNOWN",
        )

        val exception = assertThrows<BusinessException> {
            mapper.toConsent(entity)
        }
        assertEquals("mapper.consent.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    private fun entity(
        id: UUID? = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        scopes: Array<String> = arrayOf("openid"),
        consentedAt: LocalDateTime = LocalDateTime.now().minusHours(1),
        revokedAt: LocalDateTime? = null,
        revokedBy: String? = null,
        revokedById: UUID? = null,
    ): ConsentEntity {
        return ConsentEntity(
            userId = userId,
            audienceId = "audience",
            promptedByClientId = "client",
            scopes = scopes,
            consentedAt = consentedAt,
            revokedAt = revokedAt,
            revokedBy = revokedBy,
            revokedById = revokedById,
        ).apply { this.id = id }
    }
}
