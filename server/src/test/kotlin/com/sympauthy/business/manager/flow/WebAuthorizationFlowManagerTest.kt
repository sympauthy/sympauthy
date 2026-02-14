package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.NonInteractiveAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlow
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
    fun `completeAuthorizationFlowOrRedirect - Non complete if missing claims`() = runTest {
        val userId = UUID.randomUUID()
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
        }
        coEvery { collectedClaimManager.findByAttempt(authorizeAttempt) } returns emptyList()
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns false
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.getStatusAndCompleteIfNecessary(authorizeAttempt)

        assertTrue(result.missingRequiredClaims)
    }

    @Test
    fun `completeAuthorizationFlowOrRedirect - Non complete if missing validation`() = runTest {
        val userId = UUID.randomUUID()
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
        }
        coEvery { collectedClaimManager.findByAttempt(authorizeAttempt) } returns emptyList()
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
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
        }
        coEvery { collectedClaimManager.findByAttempt(authorizeAttempt) } returns emptyList()
        coEvery { authorizationFlowManager.completeAuthorization(authorizeAttempt, any()) } returns authorizeAttempt
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns true
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.getStatusAndCompleteIfNecessary(authorizeAttempt)

        assertTrue(result.complete)
    }
}
