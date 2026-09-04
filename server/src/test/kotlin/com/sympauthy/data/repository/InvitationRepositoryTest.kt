package com.sympauthy.data.repository

import com.sympauthy.data.BASE_DATE
import com.sympauthy.data.Database
import com.sympauthy.data.RepositoryFixture
import com.sympauthy.data.model.InvitationEntity
import com.sympauthy.data.withFixture
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.*

/**
 * The invitation, which is the only row read by a byte array: [InvitationRepository.findByTokenLookupHash]
 * binds one as a parameter, and the column is spelled `bytea` under one dialect and `binary varying`
 * under the other.
 */
class InvitationRepositoryTest {

    private val audienceId = "invitation-repository-test"
    private val otherAudienceId = "invitation-repository-test-other"
    private val createdById = "invitation-repository-test-admin"

    private val lookupHash = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val otherLookupHash = byteArrayOf(8, 7, 6, 5, 4, 3, 2, 1)

    @ParameterizedTest(name = "save - Round-trips the byte arrays and the claim map on {0}")
    @EnumSource(Database::class)
    fun `save - Round-trips the byte arrays and the claim map`(database: Database) = withFixture(database) {
        val invitations = repository<InvitationRepository>()
        val id = saveInvitation(claims = mapOf("email" to "ada@example.org"))

        val stored = invitations.findById(id)

        assertNotNull(stored)
        assertArrayEquals(lookupHash, stored!!.tokenLookupHash)
        assertArrayEquals(byteArrayOf(10, 20, 30), stored.hashedToken)
        assertArrayEquals(byteArrayOf(40, 50), stored.salt)
        assertEquals(mapOf("email" to "ada@example.org"), stored.claims)
        assertEquals("pending", stored.status)
        assertEquals(BASE_DATE, stored.createdAt)
    }

    @ParameterizedTest(name = "save - Round-trips an invitation carrying no claim on {0}")
    @EnumSource(Database::class)
    fun `save - Round-trips an invitation carrying no claim`(database: Database) = withFixture(database) {
        val invitations = repository<InvitationRepository>()
        val id = saveInvitation(claims = null)

        val stored = invitations.findById(id)

        assertNotNull(stored)
        assertNull(stored!!.claims)
        assertNull(stored.note)
        assertNull(stored.consumedByUserId)
        assertNull(stored.consumedAt)
        assertNull(stored.revokedAt)
    }

    @ParameterizedTest(name = "findByTokenLookupHash - Finds the invitation by its hash on {0}")
    @EnumSource(Database::class)
    fun `findByTokenLookupHash - Finds the invitation by its hash`(database: Database) = withFixture(database) {
        val invitations = repository<InvitationRepository>()
        val id = saveInvitation()
        saveInvitation(tokenLookupHash = otherLookupHash)

        val found = invitations.findByTokenLookupHash(lookupHash)

        assertEquals(id, found?.id)
    }

    @ParameterizedTest(name = "findByTokenLookupHash - Returns null when no hash matches on {0}")
    @EnumSource(Database::class)
    fun `findByTokenLookupHash - Returns null when no hash matches`(database: Database) = withFixture(database) {
        val invitations = repository<InvitationRepository>()
        saveInvitation()

        assertNull(invitations.findByTokenLookupHash(byteArrayOf(99, 98, 97)))
    }

    @ParameterizedTest(name = "findByAudienceId - Returns the invitations of the audience on {0}")
    @EnumSource(Database::class)
    fun `findByAudienceId - Returns the invitations of the audience`(database: Database) = withFixture(database) {
        val invitations = repository<InvitationRepository>()
        val id = saveInvitation()
        saveInvitation(tokenLookupHash = otherLookupHash, audienceId = otherAudienceId)

        val found = invitations.findByAudienceId(audienceId)

        assertEquals(listOf(id), found.map { it.id!! })
    }

    @ParameterizedTest(name = "findByCreatedById - Returns the invitations that author created on {0}")
    @EnumSource(Database::class)
    fun `findByCreatedById - Returns the invitations that author created`(database: Database) =
        withFixture(database) {
            val invitations = repository<InvitationRepository>()
            val id = saveInvitation()
            saveInvitation(tokenLookupHash = otherLookupHash, createdById = "invitation-repository-test-other")

            val found = invitations.findByCreatedById(createdById)

            assertEquals(listOf(id), found.map { it.id!! })
        }

    @ParameterizedTest(name = "updateStatus - Consumes the invitation on {0}")
    @EnumSource(Database::class)
    fun `updateStatus - Consumes the invitation`(database: Database) = withFixture(database) {
        val invitations = repository<InvitationRepository>()
        val userId = newUser()
        val id = saveInvitation()
        val consumedAt = BASE_DATE.plusDays(1)

        invitations.updateStatus(id, "consumed", userId, consumedAt)

        val stored = invitations.findById(id)
        assertEquals("consumed", stored?.status)
        assertEquals(userId, stored?.consumedByUserId)
        assertEquals(consumedAt, stored?.consumedAt)
    }

    @ParameterizedTest(name = "updateRevokedAt - Revokes the invitation on {0}")
    @EnumSource(Database::class)
    fun `updateRevokedAt - Revokes the invitation`(database: Database) = withFixture(database) {
        val invitations = repository<InvitationRepository>()
        val id = saveInvitation()
        val revokedAt = BASE_DATE.plusDays(1)

        invitations.updateRevokedAt(id, "revoked", revokedAt)

        val stored = invitations.findById(id)
        assertEquals("revoked", stored?.status)
        assertEquals(revokedAt, stored?.revokedAt)
    }

    private suspend fun RepositoryFixture.saveInvitation(
        tokenLookupHash: ByteArray = this@InvitationRepositoryTest.lookupHash,
        audienceId: String = this@InvitationRepositoryTest.audienceId,
        createdById: String = this@InvitationRepositoryTest.createdById,
        claims: Map<String, String>? = null
    ): UUID {
        val invitations = repository<InvitationRepository>()
        return invitations.save(
            InvitationEntity(
                audienceId = audienceId,
                tokenLookupHash = tokenLookupHash,
                hashedToken = byteArrayOf(10, 20, 30),
                salt = byteArrayOf(40, 50),
                tokenPrefix = "inv_test",
                claims = claims,
                status = "pending",
                createdBy = "administrator",
                createdById = createdById,
                createdAt = BASE_DATE,
                expiresAt = BASE_DATE.plusDays(7)
            )
        ).id!!.also { id -> deleteOnEnd { invitations.deleteById(id) } }
    }
}
