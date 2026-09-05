package com.sympauthy.business.mapper

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.data.model.InvitationEntity
import io.micronaut.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mapstruct.factory.Mappers
import java.time.LocalDateTime
import java.util.*

class InvitationMapperTest {

    private val mapper = Mappers.getMapper(InvitationMapper::class.java)

    @Test
    fun `toInvitation - maps all fields`() {
        val id = UUID.randomUUID()
        val consumedByUserId = UUID.randomUUID()
        val createdAt = LocalDateTime.now().minusHours(1)
        val expiresAt = LocalDateTime.now().plusDays(1)
        val consumedAt = LocalDateTime.now().minusMinutes(5)
        val entity = entity(
            id = id,
            claims = mapOf("email" to "user@test.com"),
            note = "note",
            createdById = "admin",
            consumedByUserId = consumedByUserId,
            createdAt = createdAt,
            expiresAt = expiresAt,
            consumedAt = consumedAt,
        )

        val invitation = mapper.toInvitation(entity)

        assertEquals(id, invitation.id)
        assertEquals("audience", invitation.audienceId)
        assertEquals("prefix", invitation.tokenPrefix)
        assertEquals(mapOf("email" to "user@test.com"), invitation.claims)
        assertEquals("note", invitation.note)
        assertEquals(InvitationStatus.PENDING, invitation.status)
        assertEquals(InvitationCreatedBy.ADMIN, invitation.createdBy)
        assertEquals("admin", invitation.createdById)
        assertEquals(consumedByUserId, invitation.consumedByUserId)
        assertEquals(createdAt, invitation.createdAt)
        assertEquals(expiresAt, invitation.expiresAt)
        assertEquals(consumedAt, invitation.consumedAt)
        assertNull(invitation.revokedAt)
    }

    @Test
    fun `toInvitation - maps a pending invitation past its expiry as expired`() {
        val entity = entity(
            status = InvitationStatus.PENDING,
            expiresAt = LocalDateTime.now().minusMinutes(1),
        )

        val invitation = mapper.toInvitation(entity)

        assertEquals(InvitationStatus.EXPIRED, invitation.status)
    }

    @Test
    fun `toInvitation - leaves a revoked invitation past its expiry revoked`() {
        val entity = entity(
            status = InvitationStatus.REVOKED,
            expiresAt = LocalDateTime.now().minusMinutes(1),
            revokedAt = LocalDateTime.now().minusMinutes(2),
        )

        val invitation = mapper.toInvitation(entity)

        assertEquals(InvitationStatus.REVOKED, invitation.status)
    }

    @Test
    fun `toInvitation - throws when entity has no id`() {
        val entity = entity(id = null)

        val exception = assertThrows<BusinessException> {
            mapper.toInvitation(entity)
        }
        assertEquals("mapper.invitation.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toInvitation - throws when status is unknown`() {
        val entity = entity(rawStatus = "UNKNOWN")

        val exception = assertThrows<BusinessException> {
            mapper.toInvitation(entity)
        }
        assertEquals("mapper.invitation.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    @Test
    fun `toInvitation - throws when createdBy is unknown`() {
        val entity = entity(rawCreatedBy = "UNKNOWN")

        val exception = assertThrows<BusinessException> {
            mapper.toInvitation(entity)
        }
        assertEquals("mapper.invitation.invalid_property", exception.detailsId)
        assertEquals(INTERNAL_SERVER_ERROR, exception.recommendedStatus)
    }

    private fun entity(
        id: UUID? = UUID.randomUUID(),
        status: InvitationStatus = InvitationStatus.PENDING,
        rawStatus: String = status.name,
        createdBy: InvitationCreatedBy = InvitationCreatedBy.ADMIN,
        rawCreatedBy: String = createdBy.name,
        claims: Map<String, String>? = null,
        note: String? = null,
        createdById: String? = null,
        consumedByUserId: UUID? = null,
        createdAt: LocalDateTime = LocalDateTime.now().minusHours(1),
        expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
        consumedAt: LocalDateTime? = null,
        revokedAt: LocalDateTime? = null,
    ): InvitationEntity {
        return InvitationEntity(
            audienceId = "audience",
            tokenLookupHash = ByteArray(0),
            hashedToken = ByteArray(0),
            salt = ByteArray(0),
            tokenPrefix = "prefix",
            claims = claims,
            note = note,
            status = rawStatus,
            createdBy = rawCreatedBy,
            createdById = createdById,
            consumedByUserId = consumedByUserId,
            createdAt = createdAt,
            expiresAt = expiresAt,
            consumedAt = consumedAt,
            revokedAt = revokedAt,
        ).apply { this.id = id }
    }
}
