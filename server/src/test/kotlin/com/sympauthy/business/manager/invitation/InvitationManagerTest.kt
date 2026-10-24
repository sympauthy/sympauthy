package com.sympauthy.business.manager.invitation

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.ClaimAclManager
import com.sympauthy.business.manager.user.ClaimValueValidator
import com.sympauthy.business.mapper.InvitationMapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.invitation.Invitation
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.UserStatus
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.InvitationAdvancedConfig
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InvitationManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var claimAclManager: ClaimAclManager

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
        val NOW: LocalDateTime = LocalDateTime.parse("2026-10-16T12:00:00")
    }

    private val expirationConfig = InvitationAdvancedConfig(
        tokenLengthInBytes = 32,
        defaultExpiration = Duration.ofDays(7),
        maxExpiration = Duration.ofDays(30),
        hashConfig = mockk()
    )

    /**
     * A claim as [InvitationManager.validateAndCleanClaims] asks after it: which audience it is for. Its value
     * is the validator's business. [audienceId] null is every audience's claim.
     */
    private fun createClaim(audienceId: String? = null): EnabledClaim = mockk {
        every { belongsToAudience(any()) } answers { audienceId == null || audienceId == firstArg() }
    }

    /**
     * A claim as [InvitationManager.applyInvitationClaims] asks after it, reading the text the invitation
     * stored back under it — which is how a value recovers the type it was cleaned to when the invitation
     * was written.
     */
    private fun createStoredClaim(audienceId: String? = null): EnabledClaim = mockk {
        every { belongsToAudience(any()) } answers { audienceId == null || audienceId == firstArg() }
    }

    /**
     * The claim reading the text an invitation stored back as the value it was cleaned to, which is how a
     * value recovers the type it lost to the invitation's own column.
     */
    private fun cleansStoredTextOf(claim: EnabledClaim) {
        every { claimValueValidator.cleanValueForClaimOrNull(claim, any()) } answers { secondArg() }
    }

    /** An account the flow is still signing up, which an invitation may give an identifier claim. */
    private fun provisionalUser(): User = mockk {
        every { sessionId } returns UUID.randomUUID()
    }

    /** A committed account an invitation is spent on. */
    private fun committedUser() =
        User(id = UUID.randomUUID(), status = UserStatus.ENABLED, creationDate = NOW, sessionId = null)

    private fun audience(id: String): Audience = Audience(id = id, tokenAudience = id)

    /** A committed account the flow resolved rather than created, which keeps the identifier it has. */
    private fun resolvedUser(): User = mockk {
        every { sessionId } returns null
    }

    /** An identifier claim as [InvitationManager.applyInvitationClaims] asks after it, its id read to log it. */
    private fun identifierClaim(): EnabledClaim = mockk {
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

    @ParameterizedTest
    @ValueSource(strings = ["2026-10-16T12:00:00", "2026-10-16T11:59:59", "-5000-01-01T00:00:00"])
    fun `resolveExpiresAt - Refuse a date that is not after the creation`(requested: String) {
        val requestedExpiresAt = LocalDateTime.parse(requested)

        val exception = assertThrows<BusinessException> {
            manager.resolveExpiresAt(NOW, requestedExpiresAt, expirationConfig)
        }
        assertEquals("invitation.expires_at.past", exception.detailsId)
        assertEquals(requestedExpiresAt.toString(), exception.values["expiresAt"])
        assertTrue(exception.recoverable)
    }

    @Test
    fun `resolveExpiresAt - Keep a date within the maximum`() {
        val requested = NOW.plusDays(3)

        assertEquals(requested, manager.resolveExpiresAt(NOW, requested, expirationConfig))
    }

    @Test
    fun `resolveExpiresAt - Bring a date beyond the maximum back to it`() {
        assertEquals(NOW.plusDays(30), manager.resolveExpiresAt(NOW, NOW.plusDays(31), expirationConfig))
    }

    @Test
    fun `resolveExpiresAt - Expire after the default when no date is asked for`() {
        assertEquals(NOW.plusDays(7), manager.resolveExpiresAt(NOW, null, expirationConfig))
    }

    @Test
    fun `resolveExpiresAt - Truncate a requested date to microseconds`() {
        val requested = LocalDateTime.parse("2026-10-20T00:00:00.123456789")

        assertEquals(
            LocalDateTime.parse("2026-10-20T00:00:00.123456"),
            manager.resolveExpiresAt(NOW, requested, expirationConfig)
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
        val context = mockk<ClaimAclContext>()
        every { claimAclManager.getTokenDoors(context, any()) } returns mockk {
            every { canBeWrittenByClient(claim) } returns false
        }

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(
                mapOf("custom_role" to "admin"),
                AUDIENCE,
                context
            )
        }
        assertEquals("invitation.claim_not_writable", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `validateAndCleanClaims - Throws when the claim is restricted to another audience`() {
        val claim = createClaim(audienceId = OTHER_AUDIENCE)
        every { claimManager.findByIdOrNull("custom_tier") } returns claim
        val context = mockk<ClaimAclContext>()
        val doors = mockk<ClaimAclManager.TokenDoors>()
        every { claimAclManager.getTokenDoors(context, any()) } returns doors

        val exception = assertThrows<BusinessException> {
            manager.validateAndCleanClaims(
                mapOf("custom_tier" to "gold"),
                AUDIENCE,
                context
            )
        }
        assertEquals("invitation.claim_of_another_audience", exception.detailsId)
        assertTrue(exception.recoverable)
        verify(exactly = 0) { doors.canBeWrittenByClient(any()) }
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
    fun `validateAndCleanClaims - Skips ACL check when the context is null`() {
        val claim = createClaim()
        every { claimManager.findByIdOrNull("custom_role") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "admin") } returns Optional.of("admin")

        val result = manager.validateAndCleanClaims(mapOf("custom_role" to "admin"), AUDIENCE, null)

        assertEquals(mapOf("custom_role" to "admin"), result)
        verify(exactly = 0) { claimAclManager.getTokenDoors(any(), any()) }
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

        val result = manager.validateToken("validToken", audience(AUDIENCE))
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
            manager.validateToken(rawToken, audience(AUDIENCE))
        }
        assertEquals("invitation.invalid_token", exception.detailsId)
    }

    @Test
    fun `validateToken - Throws when invitation is consumed`() = runTest {
        val invitation = createInvitation(status = InvitationStatus.CONSUMED)
        mockTokenLookup("consumedToken", invitation)

        val exception = assertThrows<BusinessException> {
            manager.validateToken("consumedToken", audience(AUDIENCE))
        }
        assertEquals("invitation.already_consumed", exception.detailsId)
    }

    @Test
    fun `validateToken - Throws when invitation is revoked`() = runTest {
        val invitation = createInvitation(status = InvitationStatus.REVOKED)
        mockTokenLookup("revokedToken", invitation)

        val exception = assertThrows<BusinessException> {
            manager.validateToken("revokedToken", audience(AUDIENCE))
        }
        assertEquals("invitation.revoked", exception.detailsId)
    }

    @Test
    fun `validateToken - Throws when invitation is expired`() = runTest {
        val invitation = createInvitation(status = InvitationStatus.EXPIRED)
        mockTokenLookup("expiredToken", invitation)

        val exception = assertThrows<BusinessException> {
            manager.validateToken("expiredToken", audience(AUDIENCE))
        }
        assertEquals("invitation.expired", exception.detailsId)
    }

    @Test
    fun `validateToken - Throws when audience does not match`() = runTest {
        val invitation = createInvitation(audienceId = "admin")
        mockTokenLookup("validToken", invitation)

        val exception = assertThrows<BusinessException> {
            manager.validateToken("validToken", audience(AUDIENCE))
        }
        assertEquals("invitation.audience_mismatch", exception.detailsId)
    }

    @Test
    fun `revokeInvitation - Marks the invitation revoked`() = runTest {
        val pending = createInvitation()
        val revoked = createInvitation(id = pending.id, status = InvitationStatus.REVOKED)
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.revokeIfPending(pending.id, any()) } returns 1
        coEvery { invitationRepository.findById(pending.id) } returns entity
        every { invitationMapper.toInvitation(entity) } returns revoked

        assertSame(revoked, manager.revokeInvitation(pending))
    }

    @Test
    fun `revokeInvitation - Refuses an invitation a sign-up consumed since its caller read it`() = runTest {
        val pending = createInvitation()
        val consumed = createInvitation(id = pending.id, status = InvitationStatus.CONSUMED)
        val entity = mockk<InvitationEntity>()

        coEvery { invitationRepository.revokeIfPending(pending.id, any()) } returns 0
        coEvery { invitationRepository.findById(pending.id) } returns entity
        every { invitationMapper.toInvitation(entity) } returns consumed

        val exception = assertThrows<BusinessException> {
            manager.revokeInvitation(pending)
        }

        assertEquals("invitation.cannot_revoke", exception.detailsId)
        assertEquals("description.invitation.cannot_revoke", exception.descriptionId)
        assertEquals("CONSUMED", exception.values["status"])
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

        every { claimManager.findByIdOrNull("custom_role") } returns claim
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()

        manager.applyInvitationClaims(invitation, user)

        coVerify { collectedClaimManager.update(user, match { it.size == 1 && it[0].claim == claim }) }
        coVerify(exactly = 0) { invitationRepository.consumeIfPending(any(), any(), any()) }
    }

    @Test
    fun `applyInvitationClaims - Does nothing when the invitation carries no claim`() = runTest {
        val invitationId = UUID.randomUUID()
        val invitation = createInvitation(id = invitationId, claims = null)

        manager.applyInvitationClaims(invitation, mockk())

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

        every { claimManager.findByIdOrNull("custom_region") } returns ownClaim
        every { claimManager.findByIdOrNull("custom_tier") } returns otherAudienceClaim
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()

        manager.applyInvitationClaims(invitation, user)

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

        every { claimManager.findByIdOrNull("custom_role") } returns claim

        manager.applyInvitationClaims(invitation, user)

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

        every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
        every { claimManager.findByIdOrNull("email") } returns emailClaim
        every { claimManager.findByIdOrNull("custom_role") } returns roleClaim
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()

        manager.applyInvitationClaims(invitation, user)

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

            every { claimManager.listIdentifierClaims() } returns listOf(emailClaim)
            every { claimManager.findByIdOrNull("email") } returns emailClaim

            manager.applyInvitationClaims(invitation, user)

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

        every { claimManager.findByIdOrNull("known") } returns knownClaim
        every { claimManager.findByIdOrNull("unknown") } returns null
        coEvery { collectedClaimManager.update(user, any()) } returns emptyList()

        manager.applyInvitationClaims(invitation, user)

        coVerify { collectedClaimManager.update(user, match { it.size == 1 && it[0].claim == knownClaim }) }
    }

    @Test
    fun `consumeInvitation - Marks the invitation consumed by the user`() = runTest {
        val pending = createInvitation()
        val consumed = createInvitation(id = pending.id, status = InvitationStatus.CONSUMED)
        val user = committedUser()
        val entity = mockk<InvitationEntity>()

        coJustRun { userManager.checkPromoted(user.id) }
        coEvery {
            invitationRepository.consumeIfPending(pending.id, user.id, any())
        } returns 1
        coEvery { invitationRepository.findById(pending.id) } returns entity
        every { invitationMapper.toInvitation(entity) } returns consumed

        assertSame(consumed, manager.consumeInvitation(pending, user))
        coVerify { userManager.checkPromoted(user.id) }
    }

    @Test
    fun `consumeInvitation - Refuses when another flow has already taken the invitation`() = runTest {
        val pending = createInvitation()
        val user = committedUser()

        coJustRun { userManager.checkPromoted(user.id) }
        coEvery {
            invitationRepository.consumeIfPending(pending.id, user.id, any())
        } returns 0

        val exception = assertThrows<BusinessException> {
            manager.consumeInvitation(pending, user)
        }

        assertEquals("invitation.already_consumed", exception.detailsId)
        assertFalse(exception.recoverable)
        coVerify(exactly = 0) { invitationRepository.findById(any()) }
    }
}
