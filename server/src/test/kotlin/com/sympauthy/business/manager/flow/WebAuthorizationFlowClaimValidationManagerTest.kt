package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.util.coAssertThrowsBusinessException
import com.sympauthy.business.manager.validationcode.ValidationCodeManager
import com.sympauthy.business.model.code.ValidationCode
import com.sympauthy.business.model.code.ValidationCodeMedia.EMAIL
import com.sympauthy.business.model.code.ValidationCodeReason.EMAIL_CLAIM
import com.sympauthy.business.model.code.ValidationCodeReason.PHONE_NUMBER_CLAIM
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.OpenIdClaim
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowClaimValidationManagerTest {

    @MockK
    lateinit var claimManager: ClaimManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var validationCodeManager: ValidationCodeManager

    @SpyK
    @InjectMockKs
    lateinit var manager: WebAuthorizationFlowClaimValidationManager

    @Test
    fun `getUnfilteredReasonsToSendValidationCode - Verify email`() {
        val emailClaim = mockk<Claim> {
            every { id } returns OpenIdClaim.EMAIL.id
        }
        val collectedClaim = mockk<CollectedClaim> {
            every { claim } returns emailClaim
            every { verified } returns false
        }

        every { manager.validationCodeReasons } returns listOf(EMAIL_CLAIM)
        every { manager.getClaimValidatedBy(EMAIL_CLAIM) } returns emailClaim

        val result = manager.getUnfilteredReasonsToSendValidationCode(listOf(collectedClaim))

        assertTrue(result.contains(EMAIL_CLAIM))
    }

    @Test
    fun `getUnfilteredReasonsToSendValidationCode - Do not verify email if claim already verified`() {
        val emailClaim = mockk<Claim> {
            every { id } returns OpenIdClaim.EMAIL.id
        }
        val collectedClaim = mockk<CollectedClaim> {
            every { claim } returns emailClaim
            every { verified } returns true
        }

        every { manager.validationCodeReasons } returns listOf(EMAIL_CLAIM)
        every { manager.getClaimValidatedBy(EMAIL_CLAIM) } returns emailClaim

        val result = manager.getUnfilteredReasonsToSendValidationCode(listOf(collectedClaim))

        assertFalse(result.contains(EMAIL_CLAIM))
    }

    @Test
    fun `getOrSendValidationCodes - Send code to media`() = runTest {
        val user = mockk<User>()
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
        val media = EMAIL
        val collectedClaims = listOf(mockk<CollectedClaim>())
        val reasons = listOf(EMAIL_CLAIM)
        val validationCode = mockk<ValidationCode>()

        coEvery { collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt) } returns collectedClaims
        every { manager.getReasonsToSendValidationCode(collectedClaims) } returns reasons
        coEvery {
            validationCodeManager.findLatestCodeSentByMediaDuringAttempt(
                authorizeAttempt = authorizeAttempt,
                media = media,
                includesExpired = true,
            )
        } returns null
        coEvery {
            validationCodeManager.queueRequiredValidationCodes(
                user = user,
                authorizeAttempt = authorizeAttempt,
                collectedClaims = collectedClaims,
                reasons = reasons,
            )
        } returns listOf(validationCode)

        val result = manager.getOrSendValidationCode(
            authorizeAttempt = authorizeAttempt,
            user = user,
            media = media,
        )

        assertSame(validationCode, result)
    }

    @Test
    fun `getOrSendValidationCodes - Return existing code`() = runTest {
        val user = mockk<User>()
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
        val media = EMAIL
        val collectedClaims = listOf(mockk<CollectedClaim>())
        val existingValidationCode = mockk<ValidationCode> {
            every { reasons } returns listOf(EMAIL_CLAIM)
        }

        coEvery { collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt) } returns collectedClaims
        every { manager.getReasonsToSendValidationCode(collectedClaims) } returns listOf(EMAIL_CLAIM)
        coEvery {
            validationCodeManager.findLatestCodeSentByMediaDuringAttempt(
                authorizeAttempt = authorizeAttempt,
                media = media,
                includesExpired = true,
            )
        } returns existingValidationCode

        val result = manager.getOrSendValidationCode(
            authorizeAttempt = authorizeAttempt,
            user = user,
            media = media,
        )

        assertEquals(existingValidationCode, result)
    }

    @Test
    fun `getOrSendValidationCodes - Return null if no reason to send code to media`() = runTest {
        val user = mockk<User>()
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
        val media = EMAIL
        val collectedClaims = listOf(mockk<CollectedClaim>())
        val reasons = listOf(PHONE_NUMBER_CLAIM)

        coEvery { collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt) } returns collectedClaims
        every { manager.getReasonsToSendValidationCode(collectedClaims) } returns reasons

        val result = manager.getOrSendValidationCode(
            authorizeAttempt = authorizeAttempt,
            user = user,
            media = media,
        )

        assertNull(result)
    }

    @Test
    fun `resendValidationCodes - Send new validation code if previous is expired`() = runTest {
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
        val user = mockk<User>()
        val media = EMAIL
        val expiredCode = mockk<ValidationCode>()
        val refreshedCode = mockk<ValidationCode>()
        val claims = emptyList<CollectedClaim>()

        coEvery {
            validationCodeManager.findLatestCodeSentByMediaDuringAttempt(
                authorizeAttempt = authorizeAttempt,
                media = media,
                includesExpired = true,
            )
        } returns expiredCode
        every { validationCodeManager.canBeRefreshed(expiredCode) } returns true
        coEvery { collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt) } returns claims
        coEvery {
            validationCodeManager.refreshAndQueueValidationCode(
                user = user,
                authorizeAttempt = authorizeAttempt,
                collectedClaims = claims,
                validationCode = expiredCode,
            )
        } returns ValidationCodeManager.RefreshResult(
            refreshed = true,
            validationCode = refreshedCode,
        )

        val result = manager.resendValidationCode(
            authorizeAttempt = authorizeAttempt,
            user = user,
            media = media,
        )

        assertTrue(result.resent)
        assertSame(refreshedCode, result.validationCode)
    }

    @Test
    fun `resendValidationCodes - Do nothing if no code previously sent`() = runTest {
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
        val user = mockk<User>()
        val media = EMAIL

        coEvery {
            validationCodeManager.findLatestCodeSentByMediaDuringAttempt(
                authorizeAttempt = authorizeAttempt,
                media = media,
                includesExpired = true,
            )
        } returns null

        val result = manager.resendValidationCode(
            authorizeAttempt = authorizeAttempt,
            user = user,
            media = media,
        )

        assertEquals(false, result.resent)
        assertNull(result.validationCode)
    }

    @Test
    fun `resendValidationCodes - Do nothing if previous code is not refreshable`() = runTest {
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
        val user = mockk<User>()
        val media = EMAIL
        val existingCode = mockk<ValidationCode>()

        coEvery {
            validationCodeManager.findLatestCodeSentByMediaDuringAttempt(
                authorizeAttempt = authorizeAttempt,
                media = media,
                includesExpired = true,
            )
        } returns existingCode
        every { validationCodeManager.canBeRefreshed(existingCode) } returns false

        val result = manager.resendValidationCode(
            authorizeAttempt = authorizeAttempt,
            user = user,
            media = media,
        )

        assertEquals(false, result.resent)
        assertSame(existingCode, result.validationCode)
    }

    @Test
    fun `validateClaimsByCode - Validate claims`() = runTest {
        val attemptUserId = UUID.randomUUID()
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            every { userId } returns attemptUserId
        }
        val media = EMAIL
        val reason = EMAIL_CLAIM
        val validCode = "123456"
        val validValidationCode = mockk<ValidationCode> {
            every { code } returns validCode
            every { reasons } returns listOf(reason)
            every { expired } returns false
        }
        val emailClaim = mockk<Claim>()

        coEvery {
            manager.findCodesSentDuringAttempt(authorizeAttempt = authorizeAttempt, media = media)
        } returns listOf(validValidationCode)
        every { manager.getClaimValidatedBy(reason) } returns emailClaim
        coEvery {
            collectedClaimManager.validateClaims(userId = attemptUserId, claims = listOf(emailClaim))
        } returns Unit

        manager.validateClaimsByCode(
            authorizeAttempt = authorizeAttempt,
            media = media,
            code = validCode,
        )
    }

    @Test
    fun `validateClaimsByCode - Invalid if no code is matching`() = runTest {
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
        val media = EMAIL
        val validValidationCode = mockk<ValidationCode> {
            every { code } returns "123456"
        }

        coEvery {
            manager.findCodesSentDuringAttempt(authorizeAttempt = authorizeAttempt, media = media)
        } returns listOf(validValidationCode)

        coAssertThrowsBusinessException("flow.claim_validation.invalid_code") {
            manager.validateClaimsByCode(
                authorizeAttempt = authorizeAttempt,
                media = media,
                code = "654321",
            )
        }
    }

    @Test
    fun `validateClaimsByCode - Invalid if code is expired`() = runTest {
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
        val media = EMAIL
        val validCode = "123456"
        val validValidationCode = mockk<ValidationCode> {
            every { code } returns validCode
            every { expired } returns true
        }

        coEvery {
            manager.findCodesSentDuringAttempt(authorizeAttempt = authorizeAttempt, media = media)
        } returns listOf(validValidationCode)

        coAssertThrowsBusinessException("flow.claim_validation.expired_code") {
            manager.validateClaimsByCode(
                authorizeAttempt = authorizeAttempt,
                media = media,
                code = validCode,
            )
        }
    }
}
