package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
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
import com.sympauthy.business.model.user.claim.OpenIdConnectClaimId
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
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var validationCodeManager: ValidationCodeManager

    @SpyK
    @InjectMockKs
    lateinit var manager: WebAuthorizationFlowClaimValidationManager

    @Test
    fun `getUnfilteredReasonsToSendValidationCode - Verify email from identity claims`() {
        val emailClaim = mockk<Claim> {
            every { id } returns OpenIdConnectClaimId.EMAIL
        }
        val collectedClaim = mockk<CollectedClaim> {
            every { claim } returns emailClaim
            every { verified } returns false
        }

        every { manager.validationCodeReasons } returns listOf(EMAIL_CLAIM)
        every { manager.getClaimValidatedBy(EMAIL_CLAIM) } returns emailClaim

        val result = manager.getUnfilteredReasonsToSendValidationCode(
            identifierClaims = listOf(collectedClaim),
            consentedClaims = emptyList()
        )

        assertTrue(result.contains(EMAIL_CLAIM))
    }

    @Test
    fun `getUnfilteredReasonsToSendValidationCode - Verify email from consented claims`() {
        val emailClaim = mockk<Claim> {
            every { id } returns OpenIdConnectClaimId.EMAIL
        }
        val collectedClaim = mockk<CollectedClaim> {
            every { claim } returns emailClaim
            every { verified } returns false
        }

        every { manager.validationCodeReasons } returns listOf(EMAIL_CLAIM)
        every { manager.getClaimValidatedBy(EMAIL_CLAIM) } returns emailClaim

        val result = manager.getUnfilteredReasonsToSendValidationCode(
            identifierClaims = emptyList(),
            consentedClaims = listOf(collectedClaim)
        )

        assertTrue(result.contains(EMAIL_CLAIM))
    }

    @Test
    fun `getUnfilteredReasonsToSendValidationCode - Do not verify email if claim already verified`() {
        val emailClaim = mockk<Claim> {
            every { id } returns OpenIdConnectClaimId.EMAIL
        }
        val collectedClaim = mockk<CollectedClaim> {
            every { claim } returns emailClaim
            every { verified } returns true
        }

        every { manager.validationCodeReasons } returns listOf(EMAIL_CLAIM)
        every { manager.getClaimValidatedBy(EMAIL_CLAIM) } returns emailClaim

        val result = manager.getUnfilteredReasonsToSendValidationCode(
            identifierClaims = listOf(collectedClaim),
            consentedClaims = emptyList()
        )

        assertFalse(result.contains(EMAIL_CLAIM))
    }

    @Test
    fun `getOrSendValidationCodes - Send code to media`() = runTest {
        val userId = UUID.randomUUID()
        val user = mockk<User> {
            every { id } returns userId
        }
        val consentedScopes = listOf("openid", "profile")
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            every { this@mockk.consentedScopes } returns consentedScopes
        }
        val media = EMAIL
        val identifierClaims = listOf(mockk<CollectedClaim> {
            every { claim } returns mockk { every { id } returns "email" }
        })
        val consentedClaims = listOf(mockk<CollectedClaim> {
            every { claim } returns mockk { every { id } returns "name" }
        })
        val reasons = listOf(EMAIL_CLAIM)
        val validationCode = mockk<ValidationCode>()

        coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns identifierClaims
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(userId, consentedScopes)
        } returns consentedClaims
        every {
            manager.getReasonsToSendValidationCode(
                identifierClaims = identifierClaims,
                consentedClaims = consentedClaims
            )
        } returns reasons
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
                collectedClaims = any(),
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
        val userId = UUID.randomUUID()
        val user = mockk<User> {
            every { id } returns userId
        }
        val consentedScopes = listOf("openid", "profile")
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            every { this@mockk.consentedScopes } returns consentedScopes
        }
        val media = EMAIL
        val identifierClaims = listOf(mockk<CollectedClaim> {
            every { claim } returns mockk { every { id } returns "email" }
        })
        val consentedClaims = listOf(mockk<CollectedClaim> {
            every { claim } returns mockk { every { id } returns "name" }
        })
        val existingValidationCode = mockk<ValidationCode> {
            every { reasons } returns listOf(EMAIL_CLAIM)
        }

        coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns identifierClaims
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(userId, consentedScopes)
        } returns consentedClaims
        every {
            manager.getReasonsToSendValidationCode(
                identifierClaims = identifierClaims,
                consentedClaims = consentedClaims
            )
        } returns listOf(EMAIL_CLAIM)
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
        val userId = UUID.randomUUID()
        val user = mockk<User> {
            every { id } returns userId
        }
        val consentedScopes = listOf("openid", "profile")
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            every { this@mockk.consentedScopes } returns consentedScopes
        }
        val media = EMAIL
        val identifierClaims = listOf(mockk<CollectedClaim> {
            every { claim } returns mockk { every { id } returns "email" }
        })
        val consentedClaims = listOf(mockk<CollectedClaim> {
            every { claim } returns mockk { every { id } returns "name" }
        })
        val reasons = listOf(PHONE_NUMBER_CLAIM)

        coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns identifierClaims
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(userId, consentedScopes)
        } returns consentedClaims
        every {
            manager.getReasonsToSendValidationCode(
                identifierClaims = identifierClaims,
                consentedClaims = consentedClaims
            )
        } returns reasons

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
        val userId = UUID.randomUUID()
        val user = mockk<User> {
            every { id } returns userId
        }
        val media = EMAIL
        val expiredCode = mockk<ValidationCode>()
        val refreshedCode = mockk<ValidationCode>()
        val collectedClaims = emptyList<CollectedClaim>()

        coEvery {
            validationCodeManager.findLatestCodeSentByMediaDuringAttempt(
                authorizeAttempt = authorizeAttempt,
                media = media,
                includesExpired = true,
            )
        } returns expiredCode
        every { validationCodeManager.canBeRefreshed(expiredCode) } returns true
        coEvery { collectedClaimManager.findByUserId(userId) } returns collectedClaims
        coEvery {
            validationCodeManager.refreshAndQueueValidationCode(
                user = user,
                authorizeAttempt = authorizeAttempt,
                collectedClaims = collectedClaims,
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
