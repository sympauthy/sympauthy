package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.FailedVerifyEncodedStateResult
import com.sympauthy.business.manager.auth.SuccessVerifyEncodedStateResult
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.UrlsConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowManagerTest {

    @MockK
    lateinit var authorizationFlowManager: AuthorizationFlowManager

    @MockK
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var claimValidationManager: WebAuthorizationFlowClaimValidationManager

    @MockK
    lateinit var authorizationFlowsConfig: AuthorizationFlowsConfig

    @MockK
    lateinit var uncheckedUrlsConfig: UrlsConfig

    @SpyK
    @InjectMockKs
    lateinit var manager: WebAuthorizationFlowManager

    @Test
    fun `completeAuthorizationFlowOrRedirect - Non complete if missing claims`() = runTest {
        val userId = UUID.randomUUID()
        val authorizeAttempt = mockk<AuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
        }
        coEvery { collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt) } returns emptyList()
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns false
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.getStatusAndCompleteIfNecessary(authorizeAttempt)

        assertTrue(result.missingRequiredClaims)
    }

    @Test
    fun `completeAuthorizationFlowOrRedirect - Non complete if missing validation`() = runTest {
        val userId = UUID.randomUUID()
        val authorizeAttempt = mockk<AuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
        }
        coEvery { collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt) } returns emptyList()
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns true
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns listOf(
            ValidationCodeReason.EMAIL_CLAIM,
        )

        val result = manager.getStatusAndCompleteIfNecessary(authorizeAttempt)

        assertTrue(result.missingMediaForClaimValidation.isNotEmpty())
    }

    @Test
    fun `getStatusAndCompleteIfNecessary - Complete`() = runTest {
        val userId = UUID.randomUUID()
        val authorizeAttempt = mockk<AuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
        }
        coEvery { collectedClaimManager.findClaimsReadableByAttempt(authorizeAttempt) } returns emptyList()
        coEvery { authorizationFlowManager.completeAuthorization(authorizeAttempt) } returns authorizeAttempt
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns true
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.getStatusAndCompleteIfNecessary(authorizeAttempt)

        assertTrue(result.complete)
    }

    @Test
    fun `extractFromStateVerifyThenRun - Success`() = runTest {
        val state = "valid-state"
        val webFlowId = "web-flow-id"
        val authorizeAttempt = mockk<AuthorizeAttempt> {
            every { authorizationFlowId } returns webFlowId
        }
        val webFlow = mockk<WebAuthorizationFlow>()
        val expectedResult = "test-result"

        coEvery { authorizeAttemptManager.verifyEncodedState(state) } returns SuccessVerifyEncodedStateResult(
            authorizeAttempt
        )
        every { manager.findById(webFlowId) } returns webFlow

        val result = manager.extractFromStateVerifyThenRun(state) { attempt, flow ->
            assertEquals(authorizeAttempt, attempt)
            assertEquals(webFlow, flow)
            expectedResult
        }

        assertEquals(expectedResult, result)
    }

    @Test
    fun `extractFromStateVerifyThenRun - Failed state verification`() = runTest {
        val state = "invalid-state"
        val detailsId = "auth.authorize_attempt.validate.invalid_subject"

        coEvery { authorizeAttemptManager.verifyEncodedState(state) } returns FailedVerifyEncodedStateResult(detailsId)

        val exception = assertThrows<BusinessException> {
            manager.extractFromStateVerifyThenRun(state) { _, _ ->
                fail("Should not be called")
            }
        }

        assertEquals(detailsId, exception.detailsId)
    }

    @Test
    fun `extractFromStateVerifyThenRun - Save thrown business error in authorize attempt`() = runTest {
        val state = "valid-state"
        val webFlowId = "web-flow-id"
        val authorizeAttempt = mockk<AuthorizeAttempt> {
            every { authorizationFlowId } returns webFlowId
        }
        val webFlow = mockk<WebAuthorizationFlow>()
        val businessException = mockk<BusinessException> {
            every { recoverable } returns false
        }

        coEvery { authorizeAttemptManager.verifyEncodedState(state) } returns SuccessVerifyEncodedStateResult(
            authorizeAttempt
        )
        every { manager.findById(webFlowId) } returns webFlow
        coEvery {
            authorizeAttemptManager.markAsFailedIfNotRecoverable(authorizeAttempt, businessException)
        } returns authorizeAttempt

        val exception = assertThrows<BusinessException> {
            manager.extractFromStateVerifyThenRun(state) { _, _ ->
                throw businessException
            }
        }

        assertEquals(businessException, exception)
        coVerify { authorizeAttemptManager.markAsFailedIfNotRecoverable(authorizeAttempt, businessException) }
    }
}
