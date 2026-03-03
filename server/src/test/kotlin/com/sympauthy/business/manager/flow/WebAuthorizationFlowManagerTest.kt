package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.NonInteractiveAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
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
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var uncheckedMfaConfig: MfaConfig

    @SpyK
    @InjectMockKs
    lateinit var manager: WebAuthorizationFlowManager

    @Test
    fun `findByIdOrNull - Returns WebAuthorizationFlow when found`() {
        val flowId = "test-flow-id"
        val webFlow = mockk<WebAuthorizationFlow>()

        every { authorizationFlowManager.findByIdOrNull(flowId) } returns webFlow

        val result = manager.findByIdOrNull(flowId)

        assertEquals(webFlow, result)
    }

    @Test
    fun `findByIdOrNull - Returns null when id is null`() {
        val result = manager.findByIdOrNull(null)

        assertNull(result)
    }

    @Test
    fun `findByIdOrNull - Returns null when flow is not found`() {
        val flowId = "non-existent-flow-id"

        every { authorizationFlowManager.findByIdOrNull(flowId) } returns null

        val result = manager.findByIdOrNull(flowId)

        assertNull(result)
    }

    @Test
    fun `findByIdOrNull - Returns null when flow is not a WebAuthorizationFlow`() {
        val flowId = "non-web-flow-id"
        val nonWebFlow = mockk<NonInteractiveAuthorizationFlow>()

        every { authorizationFlowManager.findByIdOrNull(flowId) } returns nonWebFlow

        val result = manager.findByIdOrNull(flowId)

        assertNull(result)
    }

    @Test
    fun `findById - Returns WebAuthorizationFlow when found`() {
        val flowId = "test-flow-id"
        val webFlow = mockk<WebAuthorizationFlow>()

        every { authorizationFlowManager.findByIdOrNull(flowId) } returns webFlow

        val result = manager.findById(flowId)

        assertEquals(webFlow, result)
    }

    @Test
    fun `findById - Throws BusinessException when flow is not found or not a WebAuthorizationFlow`() {
        val flowId = "non-existent-flow-id"

        every { authorizationFlowManager.findByIdOrNull(flowId) } returns null

        val exception = assertThrows<BusinessException> {
            manager.findById(flowId)
        }

        assertEquals("flow.web.invalid_flow", exception.detailsId)
        assertFalse(exception.recoverable)
    }

    @Test
    fun `getStatusForOnGoingAuthorizeAttempt - Non complete if missing claims`() = runTest {
        val userId = UUID.randomUUID()
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
            every { mock.mfaPassed } returns false
        }
        coEvery { collectedClaimManager.findByUserId(userId) } returns emptyList()
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns false
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.getStatusForOnGoingAuthorizeAttempt(authorizeAttempt)

        assertTrue(result.missingRequiredClaims)
    }

    @Test
    fun `getStatusForOnGoingAuthorizeAttempt - Non complete if missing validation`() = runTest {
        val userId = UUID.randomUUID()
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
            every { mock.mfaPassed } returns false
        }
        coEvery { collectedClaimManager.findByUserId(userId) } returns emptyList()
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns true
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns listOf(
            ValidationCodeReason.EMAIL_CLAIM,
        )

        val result = manager.getStatusForOnGoingAuthorizeAttempt(authorizeAttempt)

        assertTrue(result.missingMediaForClaimValidation.isNotEmpty())
    }

    @Test
    fun `getStatusAndCompleteIfNecessary - Complete`() = runTest {
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt>()
        val completeAuthorizeAttempt = mockk<CompletedAuthorizeAttempt>()
        val status = mockk<WebAuthorizationFlowStatus> {
            every { allCollectedClaims } returns emptyList()
            every { complete } returns true
        }

        coEvery { manager.getStatus(authorizeAttempt) } returns status
        coEvery {
            authorizationFlowManager.completeAuthorization(
                authorizeAttempt,
                any()
            )
        } returns completeAuthorizeAttempt

        val result = manager.getStatusAndCompleteIfNecessary(authorizeAttempt)

        assertSame(completeAuthorizeAttempt, result.first)
        assertTrue(result.second.complete)
    }
}
