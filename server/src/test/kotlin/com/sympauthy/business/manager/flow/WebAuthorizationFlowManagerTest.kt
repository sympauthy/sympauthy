package com.sympauthy.business.manager.flow

import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.UrlsConfig
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowManagerTest {

    @MockK
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var claimValidationManager: AuthorizationFlowClaimValidationManager

    @MockK
    lateinit var authorizationFlowsConfig: AuthorizationFlowsConfig

    @MockK
    lateinit var uncheckedUrlsConfig: UrlsConfig

    @SpyK
    @InjectMockKs
    lateinit var manager: WebAuthorizationFlowManager

    @Test
    fun `completeAuthorizationFlowOrRedirect - Non complete if missing claims`() = runTest {
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns false
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.completeAuthorizationFlowOrRedirect(mockk(), mockk())

        assertTrue(result.missingRequiredClaims)
    }

    @Test
    fun `completeAuthorizationFlowOrRedirect - Non complete if missing validation`() = runTest {
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns true
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns listOf(
            ValidationCodeReason.EMAIL_CLAIM,
        )

        val result = manager.completeAuthorizationFlowOrRedirect(mockk(), mockk())

        assertTrue(result.missingMediaForClaimValidation.isNotEmpty())
    }

    @Test
    fun `completeAuthorizationFlowOrRedirect - Complete`() = runTest {
        every { collectedClaimManager.areAllRequiredClaimCollected(any()) } returns true
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.completeAuthorizationFlowOrRedirect(mockk(), mockk())

        assertTrue(result.complete)
    }
}
