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
import com.sympauthy.config.model.EnabledAdvancedConfig
import com.sympauthy.config.model.HashConfig
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
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InvitationManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var claimValueValidator: ClaimValueValidator

    @MockK
    lateinit var invitationRepository: InvitationRepository

    @MockK
    lateinit var invitationHashGenerator: InvitationHashGenerator

    @MockK
    lateinit var invitationTokenGenerator: InvitationTokenGenerator

    @MockK
    lateinit var invitationMapper: InvitationMapper

    @MockK
    lateinit var uncheckedAdvancedConfig: EnabledAdvancedConfig

    @InjectMockKs
    lateinit var manager: InvitationManager

    private val testHashConfig = HashConfig(
        costParameter = 16384,
        blockSize = 8,
        parallelizationParameter = 1,
        saltLengthInBytes = 32,
        keyLengthInBytes = 32
    )

    private val testInvitationConfig = InvitationAdvancedConfig(
        tokenLengthInBytes = 32,
        defaultExpiration = Duration.ofDays(7),
        maxExpiration = Duration.ofDays(30),
        hashConfig = testHashConfig
    )

    private fun mockAdvancedConfig() {
        every { uncheckedAdvancedConfig.invitationConfig } returns testInvitationConfig
    }

    private fun createClaim(id: String, enabled: Boolean = true, dataType: ClaimDataType = ClaimDataType.STRING): Claim {
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

    // --- createInvitation ---

    @Test
    fun `createInvitation - Throws when claim does not exist`() = runTest {
        every { claimManager.findByIdOrNull("unknown_claim") } returns null

        val exception = assertThrows<BusinessException> {
            manager.createInvitation(
                audienceId = "default",
                claims = mapOf("unknown_claim" to "value"),
                note = null,
                expiresAt = null,
                createdBy = InvitationCreatedBy.ADMIN,
            )
        }
        assertEquals("invitation.unknown_claim", exception.detailsId)
        assertTrue(exception.recoverable)
    }

    @Test
    fun `createInvitation - Throws when claim is disabled`() = runTest {
        val claim = createClaim("disabled_claim", enabled = false)
        every { claimManager.findByIdOrNull("disabled_claim") } returns claim

        val exception = assertThrows<BusinessException> {
            manager.createInvitation(
                audienceId = "default",
                claims = mapOf("disabled_claim" to "value"),
                note = null,
                expiresAt = null,
                createdBy = InvitationCreatedBy.ADMIN,
            )
        }
        assertEquals("invitation.unknown_claim", exception.detailsId)
    }

    @Test
    fun `createInvitation - Validates claim value against type`() = runTest {
        val claim = createClaim("my_boolean", dataType = ClaimDataType.BOOLEAN)
        every { claimManager.findByIdOrNull("my_boolean") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "not_a_boolean") } throws
                BusinessException(recoverable = true, detailsId = "user.claim_value_validator.invalid_boolean")

        val exception = assertThrows<BusinessException> {
            manager.createInvitation(
                audienceId = "default",
                claims = mapOf("my_boolean" to "not_a_boolean"),
                note = null,
                expiresAt = null,
                createdBy = InvitationCreatedBy.ADMIN,
            )
        }
        assertEquals("user.claim_value_validator.invalid_boolean", exception.detailsId)
    }

    @Test
    fun `createInvitation - Stores cleaned claim values`() = runTest {
        mockAdvancedConfig()
        val claim = createClaim("my_boolean", dataType = ClaimDataType.BOOLEAN)
        every { claimManager.findByIdOrNull("my_boolean") } returns claim
        every { claimValueValidator.validateAndCleanValueForClaim(claim, "TRUE") } returns Optional.of("true")

        val tokenBytes = byteArrayOf(1, 2, 3)
        val salt = byteArrayOf(4, 5, 6)
        val lookupHash = byteArrayOf(7, 8, 9)
        val hashedToken = byteArrayOf(10, 11, 12)
        every { invitationTokenGenerator.generate(any()) } returnsMany listOf(tokenBytes, salt)
        every { invitationTokenGenerator.encode(tokenBytes) } returns "AQID"
        every { invitationTokenGenerator.extractPrefix("AQID") } returns "AQID"
        every { invitationHashGenerator.computeLookupHash(tokenBytes) } returns lookupHash
        coEvery { invitationHashGenerator.hash(tokenBytes, salt) } returns hashedToken

        val savedEntity = mockk<InvitationEntity>()
        coEvery { invitationRepository.save(any()) } returns savedEntity

        val expectedInvitation = createInvitation(claims = mapOf("my_boolean" to "true"))
        every { invitationMapper.toInvitation(savedEntity) } returns expectedInvitation

        val (invitation, token) = manager.createInvitation(
            audienceId = "default",
            claims = mapOf("my_boolean" to "TRUE"),
            note = null,
            expiresAt = null,
            createdBy = InvitationCreatedBy.ADMIN,
        )

        assertEquals("AQID", token)
        assertEquals(mapOf("my_boolean" to "true"), invitation.claims)

        coVerify {
            invitationRepository.save(match { entity ->
                entity.claims == mapOf("my_boolean" to "true")
            })
        }
    }

    @Test
    fun `createInvitation - Succeeds with null claims`() = runTest {
        mockAdvancedConfig()

        val tokenBytes = byteArrayOf(1, 2, 3)
        val salt = byteArrayOf(4, 5, 6)
        every { invitationTokenGenerator.generate(any()) } returnsMany listOf(tokenBytes, salt)
        every { invitationTokenGenerator.encode(tokenBytes) } returns "AQID"
        every { invitationTokenGenerator.extractPrefix("AQID") } returns "AQID"
        every { invitationHashGenerator.computeLookupHash(tokenBytes) } returns byteArrayOf(7)
        coEvery { invitationHashGenerator.hash(tokenBytes, salt) } returns byteArrayOf(8)

        val savedEntity = mockk<InvitationEntity>()
        coEvery { invitationRepository.save(any()) } returns savedEntity

        val expectedInvitation = createInvitation()
        every { invitationMapper.toInvitation(savedEntity) } returns expectedInvitation

        val (_, token) = manager.createInvitation(
            audienceId = "default",
            claims = null,
            note = null,
            expiresAt = null,
            createdBy = InvitationCreatedBy.ADMIN,
        )

        assertEquals("AQID", token)
        verify(exactly = 0) { claimManager.findByIdOrNull(any()) }
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
}