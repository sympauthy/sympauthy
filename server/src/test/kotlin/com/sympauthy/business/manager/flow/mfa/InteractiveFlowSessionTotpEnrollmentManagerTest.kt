package com.sympauthy.business.manager.flow.mfa

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.mfa.TotpEnrollment
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.config.model.EnabledAuthConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InteractiveFlowSessionTotpEnrollmentManagerTest {

    @MockK
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var uncheckedAuthConfig: EnabledAuthConfig

    @InjectMockKs
    lateinit var manager: InteractiveFlowSessionTotpEnrollmentManager

    private val userId = UUID.randomUUID()
    private val user = mockk<User> { every { id } returns userId }

    // --- getEnrollmentData / getAccount ---

    @Test
    fun `getAccount - uses the first configured identifier claim regardless of collected order`() = runTest {
        // Config order (username before email) must win over collection/DB order (email first).
        val account = accountLabelFor(
            // The second configured claim is never reached, so nothing is read off it.
            identifierClaims = listOf(claim("preferred_username"), mockk()),
            collected = listOf(
                collectedClaim("email"),
                collectedClaim("preferred_username", "alice")
            )
        )
        assertEquals("alice", account)
    }

    @Test
    fun `getAccount - falls back to the next identifier claim when the first is not collected`() = runTest {
        val account = accountLabelFor(
            identifierClaims = listOf(claim("preferred_username"), claim("email")),
            collected = listOf(collectedClaim("email", "alice@example.com"))
        )
        assertEquals("alice@example.com", account)
    }

    @Test
    fun `getAccount - skips an identifier claim whose collected value is null`() = runTest {
        val account = accountLabelFor(
            identifierClaims = listOf(claim("preferred_username"), claim("email")),
            collected = listOf(
                collectedClaim("preferred_username", null),
                collectedClaim("email", "alice@example.com")
            )
        )
        assertEquals("alice@example.com", account)
    }

    @Test
    fun `getAccount - falls back to the user id when no identifier claim is collected`() = runTest {
        val account = accountLabelFor(
            // Nothing is collected, so no configured claim is ever compared against one.
            identifierClaims = listOf(mockk()),
            collected = emptyList()
        )
        assertEquals(userId.toString(), account)
    }

    @Test
    fun `getAccount - falls back to the user id when no identifier claim is configured`() = runTest {
        val account = accountLabelFor(
            identifierClaims = emptyList(),
            // Nothing is configured, so the collected claim is never looked at.
            collected = listOf(mockk())
        )
        assertEquals(userId.toString(), account)
    }

    @Test
    fun `getEnrollmentData - derives the issuer host, builds the otpauth uri and returns the base32 secret`() =
        runTest {
            val enrollment = enrollment()
            coEvery { totpManager.initiateEnrollment(user) } returns enrollment
            every { uncheckedAuthConfig.issuer } returns "https://auth.example.com"
            coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns
                    listOf(collectedClaim("email", "alice@example.com"))
            every { claimManager.listIdentifierClaims() } returns listOf(claim("email"))
            val issuerSlot = slot<String>()
            every {
                totpManager.buildOtpauthUri(capture(issuerSlot), any(), enrollment.secret)
            } returns "otpauth://totp/stub"
            every { totpManager.encodeSecretToBase32(enrollment.secret) } returns "BASE32SECRET"

            val data = manager.getEnrollmentData(user)

            assertEquals("auth.example.com", issuerSlot.captured)
            assertEquals(enrollment, data.enrollment)
            assertEquals("otpauth://totp/stub", data.uri)
            assertEquals("BASE32SECRET", data.secret)
        }

    // --- confirmEnrollment ---

    @Test
    fun `confirmEnrollment - marks the MFA step as passed when the pending enrollment is confirmed`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val updatedSession = mockk<OnGoingInteractiveFlowSession>()
        val pending = enrollment()
        coEvery { totpManager.findPendingEnrollmentOrNull(userId) } returns pending
        coEvery { totpManager.confirmEnrollment(pending, "123456") } returns
                pending.copy(confirmedDate = LocalDateTime.now())
        coEvery { sessionManager.setMfaPassed(session) } returns updatedSession

        val result = manager.confirmEnrollment(session, user, "123456")

        assertSame(updatedSession, result)
    }

    @Test
    fun `confirmEnrollment - throws when there is no pending enrollment`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { totpManager.findPendingEnrollmentOrNull(userId) } returns null

        val exception = assertThrows<BusinessException> {
            manager.confirmEnrollment(session, user, "123456")
        }
        assertEquals("flow.mfa.totp.enroll.no_pending_enrollment", exception.detailsId)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }

    @Test
    fun `confirmEnrollment - throws when the submitted code is invalid`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val pending = enrollment()
        coEvery { totpManager.findPendingEnrollmentOrNull(userId) } returns pending
        coEvery { totpManager.confirmEnrollment(pending, "000000") } returns null

        val exception = assertThrows<BusinessException> {
            manager.confirmEnrollment(session, user, "000000")
        }
        assertEquals("flow.mfa.totp.enroll.invalid_code", exception.detailsId)
        coVerify(exactly = 0) { sessionManager.setMfaPassed(any()) }
    }

    /**
     * Sets up [getEnrollmentData] with the given identifier claims and collected claims,
     * runs it, and returns the account label passed to [TotpManager.buildOtpauthUri].
     */
    private suspend fun accountLabelFor(
        identifierClaims: List<Claim>,
        collected: List<CollectedClaim>
    ): String {
        val enrollment = enrollment()
        coEvery { totpManager.initiateEnrollment(user) } returns enrollment
        every { uncheckedAuthConfig.issuer } returns "https://auth.example.com"
        coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns collected
        every { claimManager.listIdentifierClaims() } returns identifierClaims
        val accountSlot = slot<String>()
        every { totpManager.buildOtpauthUri(any(), capture(accountSlot), any()) } returns "otpauth://totp/stub"
        every { totpManager.encodeSecretToBase32(any()) } returns "BASE32SECRET"

        manager.getEnrollmentData(user)

        return accountSlot.captured
    }

    private fun enrollment() = TotpEnrollment(
        id = UUID.randomUUID(),
        userId = userId,
        secret = byteArrayOf(1, 2, 3),
        creationDate = LocalDateTime.now(),
        confirmedDate = null
    )

    private fun claim(id: String): Claim = mockk {
        every { this@mockk.id } returns id
    }

    /** A collected claim the lookup matches on; only the one it matches is read for its value. */
    private fun collectedClaim(claimId: String): CollectedClaim {
        val claim = claim(claimId)
        return mockk {
            every { this@mockk.claim } returns claim
        }
    }

    private fun collectedClaim(claimId: String, collectedValue: Any?): CollectedClaim =
        collectedClaim(claimId).also { every { it.value } returns collectedValue }
}
