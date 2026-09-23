package com.sympauthy.business.manager.invitation

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.ClaimValueValidator
import com.sympauthy.business.mapper.InvitationMapper
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
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
    lateinit var userManager: UserManager


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

    private companion object {
        const val AUDIENCE = "default"
        const val OTHER_AUDIENCE = "billing"
    }

    /**
     * A claim as [InvitationManager.validateAndCleanClaims] asks after it: whether it is enabled and which
     * audience it is for. Its value is the validator's business. [audienceId] null is every audience's claim.
     */
    private fun createClaim(audienceId: String? = null): Claim = mockk {
        every { enabled } returns true
        every { belongsToAudience(any()) } answers { audienceId == null || audienceId == firstArg() }
    }

    /**
     * A claim as [InvitationManager.applyInvitationClaims] asks after it, reading the text the invitation
     * stored back under it — which is how a value recovers the type it was cleaned to when the invitation
     * was written.
     */
    private fun createStoredClaim(audienceId: String? = null): Claim = mockk {
        every { belongsToAudience(any()) } answers { audienceId == null || audienceId == firstArg() }
    }

    /**
     * The claim reading the text an invitation stored back as the value it was cleaned to, which is how a
     * value recovers the type it lost to the invitation's own column.
     */
    private fun cleansStoredTextOf(claim: Claim) {
        every { claimValueValidator.cleanValueForClaimOrNull(claim, any()) } answers { secondArg() }
    }

    /** An account the flow is still signing up, which an invitation may give an identifier claim. */
    private fun provisionalUser(): User = mockk {
        every { sessionId } returns UUID.randomUUID()
    }

    /** A committed account the flow resolved rather than created, which keeps the identifier it has. */
    private fun resolvedUser(): User = mockk {
        every { sessionId } returns null
    }

    /** An identifier claim as [InvitationManager.applyInvitationClaims] asks after it, its id read to log it. */
    private fun identifierClaim(): Claim = mockk {
        every { id } returns "email"
        every { belongsToAudience(any()) } returns true
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

    @Test
    fun `validateAndCleanClaims - Returns null when claims are null`() {
        val result = manager.validateAndCleanClaims(null, AUDIENCE, null)
        assertNull(result)
    }

    @Test
    fun `validateAndCleanClaims - Returns input when claims are empty`() {
        val result = manager.validateAndCleanClaims(emptyMap(), AUDIENCE, null)
        assertTrue(result.isNullOrEmpty())
    }

    @Test
    fun `validateAndCleanClaims - Throws when claim does not exist`() {
        every { claimManager.findByIdOrNull("unknown_claim") } returns null

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(mapOf("unknown_claim" to "value"), AUDIENCE, null)
        }
        assertEquals("invitation.unknown_claim", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `validateAndCleanClaims - Throws when claim is disabled`() {
        val claim = mockk<Claim> { every { enabled } returns false }
        every { claimManager.findByIdOrNull("disabled_claim") } returns claim

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(mapOf("disabled_claim" to "value"), AUDIENCE, null)
        }
        assertEquals("invitation.unknown_claim", exception.detailsId)
    }

    @Test
    fun `validateAndCleanClaims - Validates claim value against type`() {
        val claim = createClaim()
        every { claimManager.findByIdOrNull("my_boolean") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "not_a_boolean") } throws
                BusinessException(recoverable = true, detailsId = "user.claim_value_validator.invalid_boolean")

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(mapOf("my_boolean" to "not_a_boolean"), AUDIENCE, null)
        }
        assertEquals("user.claim_value_validator.invalid_boolean", exception.detailsId)
    }

    @Test
    fun `validateAndCleanClaims - Returns cleaned values`() {
        val claim = createClaim()
        every { claimManager.findByIdOrNull("my_boolean") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "TRUE") } returns Optional.of("true")

        val result = manager.validateAndCleanClaims(mapOf("my_boolean" to "TRUE"), AUDIENCE, null)
        assertEquals(mapOf("my_boolean" to "true"), result)
    }

    @Test
    fun `validateAndCleanClaims - Throws when client does not have write access to claim`() {
        val claim = createClaim()
        every { claimManager.findByIdOrNull("custom_role") } returns claim
        every { claim.canBeWrittenByClient(emptyList(), listOf("invitations:write")) } returns false

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(
                mapOf("custom_role" to "admin"),
                AUDIENCE,
                listOf("invitations:write")
            )
        }
        assertEquals("invitation.claim_not_writable", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `validateAndCleanClaims - Throws when the claim is restricted to another audience`() {
        val claim = createClaim(audienceId = OTHER_AUDIENCE)
        every { claimManager.findByIdOrNull("custom_tier") } returns claim

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(
                mapOf("custom_tier" to "gold"),
                AUDIENCE,
                listOf("users:claims:write")
            )
        }
        assertEquals("invitation.claim_of_another_audience", exception.detailsId)
        assertTrue(exception.recoverable)
        verify(exactly = 0) { claim.canBeWrittenByClient(any(), any()) }
    }

    @Test
    fun `validateAndCleanClaims - Throws when the claim is of another audience and no ACL applies`() {
        val claim = createClaim(audienceId = OTHER_AUDIENCE)
        every { claimManager.findByIdOrNull("custom_tier") } returns claim

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(mapOf("custom_tier" to "gold"), AUDIENCE, null)
        }
        assertEquals("invitation.claim_of_another_audience", exception.detailsId)
    }

    @Test
    fun `validateAndCleanClaims - Accepts a claim restricted to the invitation's own audience`() {
        val claim = createClaim(audienceId = AUDIENCE)
        every { claimManager.findByIdOrNull("custom_tier") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "gold") } returns Optional.of("gold")

        val result = manager.validateAndCleanClaims(mapOf("custom_tier" to "gold"), AUDIENCE, null)

        assertEquals(mapOf("custom_tier" to "gold"), result)
    }

    @Test
    fun `validateAndCleanClaims - Skips ACL check when clientScopeIds is null`() {
        val claim = createClaim()
        every { claimManager.findByIdOrNull("custom_role") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "admin") } returns Optional.of("admin")

        val result = manager.validateAndCleanClaims(mapOf("custom_role" to "admin"), AUDIENCE, null)

        assertEquals(mapOf("custom_role" to "admin"), result)
        verify(exactly = 0) { claim.canBeWrittenByClient(any(), any()) }
    }

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

    @Test
    fun `applyInvitationClaims - Does nothing when invitationId is null`() = runTest {
        manager.applyInvitationClaims(null, mockk())

        coVerify(exactly = 0) { invitationRepository.findById(any()) }
        coVerify(exactly = 0) { collectedClaimManager.update(any(), any()) }
    }

    @Test
    fun `applyInvitationClaims - Applies the claims to the user without consuming the invitation`() = runTest {
        val invitationId = UUID.randomUUID()
        val invitation = createInvitation(
            id = invitationId,
            claims = mapOf("custom_role" to "admin")
        )
        val claim = createStoredClaim()
        cleansStoredTextOf(claim)
        val user = provisionalUser()
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation
        every { claimManager.findByIdOrNull("custom_role") } returns claim
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()

        manager.applyInvitationClaims(invitationId, user)

        coVerify { collectedClaimManager.update(user, match { it.size == 1 && it[0].claim == claim }) }
        coVerify(exactly = 0) { invitationRepository.consumeIfPending(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `applyInvitationClaims - Does nothing when the invitation carries no claim`() = runTest {
        val invitationId = UUID.randomUUID()
        val invitation = createInvitation(id = invitationId, claims = null)
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation

        manager.applyInvitationClaims(invitationId, mockk())

        coVerify(exactly = 0) { collectedClaimManager.update(any(), any()) }
    }

    @Test
    fun `applyInvitationClaims - Skips a claim the audience lost since the invitation was created`() = runTest {
        val invitationId = UUID.randomUUID()
        val invitation = createInvitation(
            id = invitationId,
            audienceId = AUDIENCE,
            claims = mapOf("custom_region" to "eu-west", "custom_tier" to "gold")
        )
        val ownClaim = createStoredClaim(audienceId = AUDIENCE)
        cleansStoredTextOf(ownClaim)
        val otherAudienceClaim = createStoredClaim(audienceId = OTHER_AUDIENCE)
        val user = provisionalUser()
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation
        every { claimManager.findByIdOrNull("custom_region") } returns ownClaim
        every { claimManager.findByIdOrNull("custom_tier") } returns otherAudienceClaim
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()

        manager.applyInvitationClaims(invitationId, user)

        coVerify { collectedClaimManager.update(user, match { it.size == 1 && it[0].claim == ownClaim }) }
    }

    @Test
    fun `applyInvitationClaims - Leaves alone a value the claim no longer accepts`() = runTest {
        // The configuration moved under the invitation. Writing the text as it stands would give the
        // account a value no read of that claim could match.
        val invitationId = UUID.randomUUID()
        val invitation = createInvitation(id = invitationId, claims = mapOf("custom_role" to "admin"))
        val claim = createStoredClaim()
        every { claim.id } returns "custom_role"
        every { claimValueValidator.cleanValueForClaimOrNull(claim, "admin") } returns null
        val user = provisionalUser()
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation
        every { claimManager.findByIdOrNull("custom_role") } returns claim

        manager.applyInvitationClaims(invitationId, user)

        coVerify(exactly = 0) { collectedClaimManager.update(any(), any()) }
    }

    @Test
    fun `applyInvitationClaims - Leave an identifier claim alone on an account the flow resolved`() = runTest {
        val invitationId = UUID.randomUUID()
        val invitation = createInvitation(
            id = invitationId,
            claims = mapOf("email" to "invited@example.com", "custom_role" to "admin")
        )
        val emailClaim = identifierClaim()
        val roleClaim = createStoredClaim()
        cleansStoredTextOf(roleClaim)
        val user = resolvedUser()
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation
        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
        every { claimManager.findByIdOrNull("email") } returns emailClaim
        every { claimManager.findByIdOrNull("custom_role") } returns roleClaim
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()

        manager.applyInvitationClaims(invitationId, user)

        coVerify { collectedClaimManager.update(user, match { it.size == 1 && it[0].claim == roleClaim }) }
    }

    @Test
    fun `applyInvitationClaims - Write nothing when an account the flow resolved is sent identifiers only`() =
        runTest {
            val invitationId = UUID.randomUUID()
            val invitation = createInvitation(
                id = invitationId,
                claims = mapOf("email" to "invited@example.com")
            )
            val emailClaim = identifierClaim()
            val user = resolvedUser()
            val entity = mockk<InvitationEntity>()

            coEvery { invitationRepository.findById(invitationId) } returns entity
            every { invitationMapper.toInvitation(entity) } returns invitation
            every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
            every { claimManager.findByIdOrNull("email") } returns emailClaim

            manager.applyInvitationClaims(invitationId, user)

            coVerify(exactly = 0) { collectedClaimManager.update(any(), any()) }
        }

    @Test

    fun `applyInvitationClaims - Skips unknown claims`() = runTest {
        val invitationId = UUID.randomUUID()
        val invitation = createInvitation(
            id = invitationId,
            claims = mapOf("known" to "value", "unknown" to "value")
        )
        val knownClaim = createStoredClaim()
        cleansStoredTextOf(knownClaim)
        val user = provisionalUser()
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation
        every { claimManager.findByIdOrNull("known") } returns knownClaim
        every { claimManager.findByIdOrNull("unknown") } returns null
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()

        manager.applyInvitationClaims(invitationId, user)

        coVerify { collectedClaimManager.update(user, match { it.size == 1 && it[0].claim == knownClaim }) }
    }

    @Test
    fun `consumeInvitation - Marks the invitation consumed by the user`() = runTest {
        val invitationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val invitation = createInvitation(id = invitationId, status = InvitationStatus.CONSUMED)
        val entity = mockk<InvitationEntity>()

        coJustRun { userManager.checkPromoted(userId) }
        coEvery {
            invitationRepository.consumeIfPending(invitationId, "CONSUMED", "PENDING", userId, any())
        } returns 1
        coEvery { invitationRepository.findById(invitationId) } returns entity
        every { invitationMapper.toInvitation(entity) } returns invitation

        assertSame(invitation, manager.consumeInvitation(invitationId, userId))
    }

    @Test
    fun `consumeInvitation - Refuses when another flow has already taken the invitation`() = runTest {
        val invitationId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        coJustRun { userManager.checkPromoted(userId) }
        coEvery {
            invitationRepository.consumeIfPending(invitationId, "CONSUMED", "PENDING", userId, any())
        } returns 0

        val exception = assertThrows<BusinessException> {
            manager.consumeInvitation(invitationId, userId)
        }

        assertEquals("invitation.already_consumed", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { invitationRepository.findById(any()) }
    }
}
