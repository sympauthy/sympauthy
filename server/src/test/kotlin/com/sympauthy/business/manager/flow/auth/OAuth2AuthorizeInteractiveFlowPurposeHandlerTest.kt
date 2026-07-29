package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.UserGrantScopesResult
import com.sympauthy.business.manager.auth.UserScopeGrantingManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowPurposeStepResult
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.auth.OAuth2AuthorizeInteractiveFlowStatus
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.EnabledFeaturesConfig
import com.sympauthy.config.model.EnabledMfaConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import jakarta.inject.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class OAuth2AuthorizeInteractiveFlowPurposeHandlerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var claimValidationManager: InteractiveAuthFlowSessionClaimValidationManager

    @MockK
    lateinit var scopeGrantingManager: UserScopeGrantingManager

    @MockK
    lateinit var consentManager: ConsentManager

    @MockK
    lateinit var uncheckedMfaConfig: EnabledMfaConfig

    @MockK
    lateinit var uncheckedFeaturesConfig: EnabledFeaturesConfig

    @MockK
    lateinit var clientManager: ClientManager

    private val testAudience = Audience(id = "test-audience", tokenAudience = "test-audience")
    private val clientManagerProvider: Provider<ClientManager> = Provider { clientManager }

    @SpyK
    @InjectMockKs
    lateinit var handler: OAuth2AuthorizeInteractiveFlowPurposeHandler

    // --- getNextStep ---

    @Test
    fun `getNextStep - Missing user without invitation returns Pending SignIn`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(missingUser = true)
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of(invitationId = null)

        assertEquals(InteractiveFlowStep.SignIn, pendingStep(handler.getNextStep(session)))
    }

    @Test
    fun `getNextStep - Missing user with invitation returns Pending SignUp`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(missingUser = true)
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of(invitationId = UUID.randomUUID())

        assertEquals(InteractiveFlowStep.SignUp, pendingStep(handler.getNextStep(session)))
    }

    @Test
    fun `getNextStep - Missing MFA returns Pending Mfa`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(missingMfa = true)

        assertEquals(InteractiveFlowStep.Mfa, pendingStep(handler.getNextStep(session)))
    }

    @Test
    fun `getNextStep - Missing required claims returns Pending CollectClaims`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(missingRequiredClaims = true)

        assertEquals(InteractiveFlowStep.CollectClaims, pendingStep(handler.getNextStep(session)))
    }

    @Test
    fun `getNextStep - Missing validation media returns Pending ValidateClaims with the first media`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(
            missingMediaForClaimValidation = listOf(ValidationCodeMedia.EMAIL)
        )

        assertEquals(
            InteractiveFlowStep.ValidateClaims(ValidationCodeMedia.EMAIL),
            pendingStep(handler.getNextStep(session))
        )
    }

    @Test
    fun `getNextStep - All steps satisfied returns Resolved`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus()

        val result = handler.getNextStep(session)

        assertInstanceOf(InteractiveFlowPurposeStepResult.Resolved::class.java, result)
        assertSame(session, result.session)
    }

    // --- complete effect ---

    @Test
    fun `complete - Marks as complete and saves consent when scopes are granted`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val grantedScopeObjects = listOf(mockkScope("read"))
        val onGoingSession = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(clientId = clientId, grantedScopes = listOf("read"))
        val completedSession = mockk<CompletedInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
        }

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns false
        coEvery { collectedClaimManager.findByUserId(userId) } returns emptyList()
        coEvery { scopeGrantingManager.grantScopes(onGoingSession, emptyList()) } returns grantScopesResultOf(grantedScopeObjects)
        coEvery { oauth2Manager.setGrantedScopes(onGoingSession, grantedScopeObjects, any()) } returns oauth2AfterGranted
        coEvery { sessionManager.markAsComplete(onGoingSession) } returns completedSession
        coEvery { clientManager.findClientById(clientId) } returns mockClient(clientId)
        coEvery { consentManager.saveConsent(userId, any(), clientId, any()) } returns mockk()

        val result = handler.complete(onGoingSession)

        assertSame(completedSession, result)
        coVerify(exactly = 1) { consentManager.saveConsent(userId, testAudience.id, clientId, any()) }
    }

    @Test
    fun `complete - Marks as failed when no scope granted and access without scope is disallowed`() = runTest {
        val userId = UUID.randomUUID()
        val onGoingSession = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(clientId = "client-id", grantedScopes = emptyList(), consentedScopes = emptyList())
        val failedSession = mockk<FailedInteractiveFlowSession>()

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns false
        coEvery { collectedClaimManager.findByUserId(userId) } returns emptyList()
        coEvery { scopeGrantingManager.grantScopes(onGoingSession, emptyList()) } returns grantScopesResultOf(emptyList())
        coEvery { oauth2Manager.setGrantedScopes(onGoingSession, emptyList(), any()) } returns oauth2AfterGranted
        coEvery { sessionManager.markAsFailedIfNotRecoverable(onGoingSession, any()) } returns failedSession

        val result = handler.complete(onGoingSession)

        assertSame(failedSession, result)
        coVerify {
            sessionManager.markAsFailedIfNotRecoverable(
                session = onGoingSession,
                error = match<BusinessException> { it.detailsId == "flow.authorization_flow.complete.no_scope" }
            )
        }
    }

    @Test
    fun `complete - Marks as complete when no scope granted but access without scope is allowed`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val onGoingSession = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(clientId = clientId, grantedScopes = emptyList(), consentedScopes = emptyList())
        val completedSession = mockk<CompletedInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
        }

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns true
        coEvery { collectedClaimManager.findByUserId(userId) } returns emptyList()
        coEvery { scopeGrantingManager.grantScopes(onGoingSession, emptyList()) } returns grantScopesResultOf(emptyList())
        coEvery { oauth2Manager.setGrantedScopes(onGoingSession, emptyList(), any()) } returns oauth2AfterGranted
        coEvery { sessionManager.markAsComplete(onGoingSession) } returns completedSession
        coEvery { clientManager.findClientById(clientId) } returns mockClient(clientId)
        coEvery { consentManager.saveConsent(userId, any(), clientId, any()) } returns mockk()

        val result = handler.complete(onGoingSession)

        assertSame(completedSession, result)
    }

    // --- computeStatus (status computation used by getCurrentStep) ---

    @Test
    fun `computeStatus - Missing required claims when not all are collected`() = runTest {
        val userId = UUID.randomUUID()
        val session = onGoingSessionMock(userId, mfaPassed = false)
        val oauth2 = stubClaimsAndMfa(session, userId, mfaEnabled = false, allRequiredCollected = false)

        assertTrue(handler.computeStatus(session, oauth2).missingRequiredClaims)
    }

    @Test
    fun `computeStatus - Missing validation media when a claim needs validation`() = runTest {
        val userId = UUID.randomUUID()
        val session = onGoingSessionMock(userId, mfaPassed = false)
        val oauth2 = stubClaimsAndMfa(
            session, userId, mfaEnabled = false, allRequiredCollected = true,
            reasons = listOf(ValidationCodeReason.EMAIL_CLAIM)
        )

        assertTrue(handler.computeStatus(session, oauth2).missingMediaForClaimValidation.isNotEmpty())
    }

    @Test
    fun `computeStatus - Missing MFA when enabled and not passed`() = runTest {
        val userId = UUID.randomUUID()
        val session = onGoingSessionMock(userId, mfaPassed = false)
        val oauth2 = stubClaimsAndMfa(session, userId, mfaEnabled = true, allRequiredCollected = true)

        assertTrue(handler.computeStatus(session, oauth2).missingMfa)
    }

    @Test
    fun `computeStatus - Not missing MFA when already passed`() = runTest {
        val userId = UUID.randomUUID()
        val session = onGoingSessionMock(userId, mfaPassed = true)
        val oauth2 = stubClaimsAndMfa(session, userId, mfaEnabled = true, allRequiredCollected = true)

        assertFalse(handler.computeStatus(session, oauth2).missingMfa)
    }

    // --- helpers ---

    private fun pendingStep(result: InteractiveFlowPurposeStepResult): InteractiveFlowStep {
        return assertInstanceOf(InteractiveFlowPurposeStepResult.Pending::class.java, result).step
    }

    private fun onGoingSessionMock(userId: UUID, mfaPassed: Boolean) = mockk<OnGoingInteractiveFlowSession> {
        every { this@mockk.userId } returns userId
        every { this@mockk.mfaPassed } returns mfaPassed
    }

    private fun stubClaimsAndMfa(
        session: OnGoingInteractiveFlowSession,
        userId: UUID,
        mfaEnabled: Boolean,
        allRequiredCollected: Boolean,
        reasons: List<ValidationCodeReason> = emptyList()
    ): InteractiveFlowSessionOAuth2 {
        val consentedScopes = listOf("openid", "profile")
        every { uncheckedMfaConfig.enabled } returns mfaEnabled
        coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns emptyList()
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(userId, consentedScopes)
        } returns emptyList()
        every {
            consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(any(), consentedScopes)
        } returns allRequiredCollected
        every { claimValidationManager.getReasonsToSendValidationCode(any(), any()) } returns reasons
        return oauth2Of(consentedScopes = consentedScopes)
    }

    private fun oauth2Of(
        clientId: String = "client-id",
        consentedScopes: List<String>? = null,
        grantedScopes: List<String>? = null,
        invitationId: UUID? = null,
    ): InteractiveFlowSessionOAuth2 {
        return InteractiveFlowSessionOAuth2(
            sessionId = UUID.randomUUID(),
            clientId = clientId,
            redirectUri = "https://example.com/callback",
            requestedScopes = emptyList(),
            state = "state",
            nonce = "nonce",
            consentedScopes = consentedScopes,
            consentedAt = consentedScopes?.let { LocalDateTime.now() },
            consentedBy = consentedScopes?.let { ConsentedBy.AUTO },
            grantedScopes = grantedScopes,
            invitationId = invitationId,
        )
    }

    private fun grantScopesResultOf(grantedScopes: List<Scope>): UserGrantScopesResult {
        return UserGrantScopesResult(
            requestedScopes = grantedScopes,
            results = listOf(
                ScopeGrantingMethodResult(
                    grantedScopes = grantedScopes,
                    declinedScopes = emptyList()
                )
            )
        )
    }

    private fun mockkScope(scope: String): Scope {
        return mockk {
            every { this@mockk.scope } returns scope
        }
    }

    private fun mockClient(id: String = "test-client"): Client {
        return mockk {
            every { this@mockk.id } returns id
            every { audience } returns testAudience
        }
    }

    private fun createOnGoingSession(userId: UUID?): OnGoingInteractiveFlowSession {
        return OnGoingInteractiveFlowSession(
            id = UUID.randomUUID(),
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = userId
        )
    }
}
