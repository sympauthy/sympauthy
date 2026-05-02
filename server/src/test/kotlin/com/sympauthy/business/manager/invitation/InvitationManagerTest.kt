package com.sympauthy.business.manager.invitation

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.ClaimValueValidator
import com.sympauthy.business.mapper.InvitationMapper
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.data.model.InvitationEntity
import com.sympauthy.data.repository.InvitationRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InvitationManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var claimValueValidator: ClaimValueValidator

    @MockK
    lateinit var collectedClaimManager: com.sympauthy.business.manager.user.CollectedClaimManager

    @MockK
    lateinit var userManager: com.sympauthy.business.manager.user.UserManager

    @MockK
    lateinit var invitationRepository: InvitationRepository

    @MockK
    lateinit var invitationHashGenerator: InvitationHashGenerator

    @MockK
    lateinit var invitationTokenGenerator: InvitationTokenGenerator

    @MockK
    lateinit var invitationMapper: InvitationMapper

    @MockK
    lateinit var uncheckedAdvancedConfig: AdvancedConfig

    @InjectMockKs
    lateinit var manager: InvitationManager

    private fun createClaim(
        id: String,
        enabled: Boolean = true,
        dataType: ClaimDataType = ClaimDataType.STRING
    ): Claim {
        return mockk {
            every { this@mockk.id } returns id
            every { this@mockk.enabled } returns enabled
            every { this@mockk.dataType } returns dataType
            every { this@mockk.allowedValues } returns null
        }
    }

    private fun createInvitation(
        id: UUID = UUID.randomUUID(),
        audienceId: String = "default",
        status: InvitationStatus = InvitationStatus.PENDING,
        claims: Map<String, String>? = null
    ): Invitation {
        return Invitation(
            id = id,
            audienceId = audienceId,
            tokenPrefix = "abcd1234",
            claims = claims,
            note = null,
            status = status,
            createdBy = InvitationCreatedBy.ADMIN,
            createdById = null,
            consumedByUserId = null,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusDays(7),
            consumedAt = null,
            revokedAt = null,
        )
    }

    // --- validateAndCleanClaims ---

    @Test
    fun `validateAndCleanClaims - Returns null when claims are null`() {
        val result = manager.validateAndCleanClaims(null, null)
        assertNull(result)
    }

    @Test
    fun `validateAndCleanClaims - Returns input when claims are empty`() {
        val result = manager.validateAndCleanClaims(emptyMap(), null)
        assertTrue(result.isNullOrEmpty())
    }

    @Test
    fun `validateAndCleanClaims - Throws when claim does not exist`() {
        every { claimManager.findByIdOrNull("unknown_claim") } returns null

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(mapOf("unknown_claim" to "value"), null)
        }
        assertEquals("invitation.unknown_claim", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `validateAndCleanClaims - Throws when claim is disabled`() {
        val claim = createClaim("disabled_claim", enabled = false)
        every { claimManager.findByIdOrNull("disabled_claim") } returns claim

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(mapOf("disabled_claim" to "value"), null)
        }
        assertEquals("invitation.unknown_claim", exception.detailsId)
    }

    @Test
    fun `validateAndCleanClaims - Validates claim value against type`() {
        val claim = createClaim("my_boolean", dataType = ClaimDataType.BOOLEAN)
        every { claimManager.findByIdOrNull("my_boolean") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "not_a_boolean") } throws
                BusinessException(recoverable = true, detailsId = "user.claim_value_validator.invalid_boolean")

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(mapOf("my_boolean" to "not_a_boolean"), null)
        }
        assertEquals("user.claim_value_validator.invalid_boolean", exception.detailsId)
    }

    @Test
    fun `validateAndCleanClaims - Returns cleaned values`() {
        val claim = createClaim("my_boolean", dataType = ClaimDataType.BOOLEAN)
        every { claimManager.findByIdOrNull("my_boolean") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "TRUE") } returns Optional.of("true")

        val result = manager.validateAndCleanClaims(mapOf("my_boolean" to "TRUE"), null)
        assertEquals(mapOf("my_boolean" to "true"), result)
    }

    @Test
    fun `validateAndCleanClaims - Throws when client does not have write access to claim`() {
        val claim = createClaim("custom_role")
        every { claimManager.findByIdOrNull("custom_role") } returns claim
        every { claim.canBeWrittenByClient(emptyList(), listOf("invitations:write")) } returns false

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(
                mapOf("custom_role" to "admin"),
                listOf("invitations:write")
            )
        }
        assertEquals("invitation.claim_not_writable", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `validateAndCleanClaims - Skips ACL check when clientScopeIds is null`() {
        val claim = createClaim("custom_role")
        every { claimManager.findByIdOrNull("custom_role") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "admin") } returns Optional.of("admin")

        val result = manager.validateAndCleanClaims(mapOf("custom_role" to "admin"), null)

        assertEquals(mapOf("custom_role" to "admin"), result)
        verify(exactly = 0) { claim.canBeWrittenByClient(any(), any()) }
    }

    // --- validateToken ---

    private fun mockTokenLookup(rawToken: String, invitation: Invitation) {
        val tokenBytes = byteArrayOf(1, 2, 3)
        val lookupHash = byteArrayOf(4, 5, 6)
        val salt = byteArrayOf(7, 8, 9)
        val hashedToken = byteArrayOf(10, 11, 12)
        val entity = mockk<InvitationEntity> {
            every { this@mockk.salt } returns salt
            every { this@mockk.hashedToken } returns hashedToken
        }

        every { invitationTokenGenerator.decode(rawToken) } returns tokenBytes
        every { invitationHashGenerator.computeLookupHash(tokenBytes) } returns lookupHash
        coEvery { invitationRepository.findByTokenLookupHash(lookupHash) } returns entity
        coEvery { invitationHashGenerator.verify(tokenBytes, salt, hashedToken) } returns true
        every { invitationMapper.toInvitation(entity) } returns invitation
    }

    @Test
    fun `validateToken - Returns invitation when valid`() = runTest {
        val invitation = createInvitation(audienceId = "default")
        mockTokenLookup("validToken", invitation)

        val result = manager.validateToken("validToken", "default")
        assertEquals(invitation, result)
    }

    @Test
    fun `validateToken - Throws when token not found`() = runTest {
        val rawToken = "unknownToken"
        val tokenBytes = byteArrayOf(1, 2, 3)

        every { invitationTokenGenerator.decode(rawToken) } returns tokenBytes
        every { invitationHashGenerator.computeLookupHash(tokenBytes) } returns byteArrayOf(4)
        coEvery { invitationRepository.findByTokenLookupHash(byteArrayOf(4)) } returns null

        val exception = assertThrows<BusinessException> {
            manager.validateToken(rawToken, "default")
        }
        assertEquals("invitation.invalid_token", exception.detailsId)
    }

    @Test
    fun `validateToken - Throws when invitation is consumed`() = runTest {
        val invitation = createInvitation(status = InvitationStatus.CONSUMED)
        mockTokenLookup("consumedToken", invitation)

        val exception = assertThrows<BusinessException> {
            manager.validateToken("consumedToken", "default")
        }
        assertEquals("invitation.already_consumed", exception.detailsId)
    }

    @Test
    fun `validateToken - Throws when invitation is revoked`() = runTest {
        val invitation = createInvitation(status = InvitationStatus.REVOKED)
        mockTokenLookup("revokedToken", invitation)

        val exception = assertThrows<BusinessException> {
            manager.validateToken("revokedToken", "default")
        }
        assertEquals("invitation.revoked", exception.detailsId)
    }

    @Test
    fun `validateToken - Throws when invitation is expired`() = runTest {
        val invitation = createInvitation(status = InvitationStatus.EXPIRED)
        mockTokenLookup("expiredToken", invitation)

        val exception = assertThrows<BusinessException> {
            manager.validateToken("expiredToken", "default")
        }
        assertEquals("invitation.expired", exception.detailsId)
    }

    @Test
    fun `validateToken - Throws when audience does not match`() = runTest {
        val invitation = createInvitation(audienceId = "admin")
        mockTokenLookup("validToken", invitation)

        val exception = assertThrows<BusinessException> {
            manager.validateToken("validToken", "default")
        }
        assertEquals("invitation.audience_mismatch", exception.detailsId)
    }

    // --- revokeInvitation ---

    @Test
    fun `revokeInvitation - Throws when invitation is not pending`() = runTest {
        val invitationId = UUID.randomUUID()
        val invitation = createInvitation(id = invitationId, status = InvitationStatus.CONSUMED)

        coEvery { invitationRepository.findById(invitationId) } returns mockk()
        every { invitationMapper.toInvitation(any<InvitationEntity>()) } returns invitation

        val exception = assertThrows<BusinessException> {
            manager.revokeInvitation(invitationId)
        }
        assertEquals("invitation.cannot_revoke", exception.detailsId)
    }

    // --- applyInvitationClaimsAndConsume ---

    @Test
    fun `applyInvitationClaimsAndConsume - Does nothing when invitationId is null`() = runTest {
        manager.applyInvitationClaimsAndConsume(null, UUID.randomUUID())

        coVerify(exactly = 0) { invitationRepository.findById(any()) }
        coVerify(exactly = 0) { collectedClaimManager.update(any(), any()) }
    }

    @Test
    fun `applyInvitationClaimsAndConsume - Applies claims and consumes invitation`() = runTest {
        val invitationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val invitation = createInvitation(
            id = invitationId,
            claims = mapOf("custom_role" to "admin")
        )
        val claim = createClaim("custom_role")
        val user = mockk<com.sympauthy.business.model.user.User>()
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation
        every { claimManager.findByIdOrNull("custom_role") } returns claim
        coEvery { userManager.findById(userId) } returns user
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()
        coEvery { invitationRepository.updateStatus(invitationId, any(), any(), any()) } just runs

        manager.applyInvitationClaimsAndConsume(invitationId, userId)

        coVerify { collectedClaimManager.update(user, match { it.size == 1 && it[0].claim == claim }) }
        coVerify { invitationRepository.updateStatus(invitationId, "CONSUMED", userId, any()) }
    }

    @Test
    fun `applyInvitationClaimsAndConsume - Consumes invitation even with no claims`() = runTest {
        val invitationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val invitation = createInvitation(id = invitationId, claims = null)
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation
        coEvery { invitationRepository.updateStatus(invitationId, any(), any(), any()) } just runs

        manager.applyInvitationClaimsAndConsume(invitationId, userId)

        coVerify(exactly = 0) { collectedClaimManager.update(any(), any()) }
        coVerify { invitationRepository.updateStatus(invitationId, "CONSUMED", userId, any()) }
    }

    @Test
    fun `applyInvitationClaimsAndConsume - Skips unknown claims`() = runTest {
        val invitationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val invitation = createInvitation(
            id = invitationId,
            claims = mapOf("known" to "value", "unknown" to "value")
        )
        val knownClaim = createClaim("known")
        val user = mockk<com.sympauthy.business.model.user.User>()
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation
        every { claimManager.findByIdOrNull("known") } returns knownClaim
        every { claimManager.findByIdOrNull("unknown") } returns null
        coEvery { userManager.findById(userId) } returns user
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()
        coEvery { invitationRepository.updateStatus(invitationId, any(), any(), any()) } just runs

        manager.applyInvitationClaimsAndConsume(invitationId, userId)

        coVerify { collectedClaimManager.update(user, match { it.size == 1 && it[0].claim == knownClaim }) }
    }
}
