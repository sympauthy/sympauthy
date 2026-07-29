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
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.InteractiveFlowStatus
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
import org.junit.jupiter.api.Assertions.assertSame
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

    // --- getCurrentStep dispatch ---

    @Test
    fun `getCurrentStep - Failed session maps to Error`() = runTest {
        val session = mockk<FailedInteractiveFlowSession>()

        val result = handler.getCurrentStep(session)

        assertSame(session, result.session)
        assertEquals(InteractiveFlowStep.Error, result.step)
    }

    @Test
    fun `getCurrentStep - Completed session maps to Complete without re-granting`() = runTest {
        val session = mockk<CompletedInteractiveFlowSession>()

        val result = handler.getCurrentStep(session)

        assertSame(session, result.session)
        assertEquals(InteractiveFlowStep.Complete, result.step)
    }

    @Test
    fun `getCurrentStep - Missing user without invitation maps to SignIn`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { handler.computeStatus(session) } returns InteractiveFlowStatus(missingUser = true)
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of(invitationId = null)

        val result = handler.getCurrentStep(session)

        assertEquals(InteractiveFlowStep.SignIn, result.step)
    }

    @Test
    fun `getCurrentStep - Missing user with invitation maps to SignUp`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { handler.computeStatus(session) } returns InteractiveFlowStatus(missingUser = true)
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of(invitationId = UUID.randomUUID())

        val result = handler.getCurrentStep(session)

        assertEquals(InteractiveFlowStep.SignUp, result.step)
    }

    @Test
    fun `getCurrentStep - Missing MFA maps to Mfa`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { handler.computeStatus(session) } returns InteractiveFlowStatus(missingMfa = true)

        val result = handler.getCurrentStep(session)

        assertEquals(InteractiveFlowStep.Mfa, result.step)
    }

    @Test
    fun `getCurrentStep - Missing required claims maps to CollectClaims`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { handler.computeStatus(session) } returns InteractiveFlowStatus(missingRequiredClaims = true)

        val result = handler.getCurrentStep(session)

        assertEquals(InteractiveFlowStep.CollectClaims, result.step)
    }

    @Test
    fun `getCurrentStep - Missing validation media maps to ValidateClaims with the first media`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { handler.computeStatus(session) } returns InteractiveFlowStatus(
            missingMediaForClaimValidation = listOf(ValidationCodeMedia.EMAIL)
        )

        val result = handler.getCurrentStep(session)

        assertEquals(InteractiveFlowStep.ValidateClaims(ValidationCodeMedia.EMAIL), result.step)
    }

    @Test
    fun `getCurrentStep - All steps satisfied completes and maps to Complete`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val completed = mockk<CompletedInteractiveFlowSession>()
        coEvery { handler.computeStatus(session) } returns InteractiveFlowStatus()
        coEvery { handler.complete(session) } returns completed

        val result = handler.getCurrentStep(session)

        assertSame(completed, result.session)
        assertEquals(InteractiveFlowStep.Complete, result.step)
    }

    @Test
    fun `getCurrentStep - Completion failing maps to Error`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val failed = mockk<FailedInteractiveFlowSession>()
        coEvery { handler.computeStatus(session) } returns InteractiveFlowStatus()
        coEvery { handler.complete(session) } returns failed

        val result = handler.getCurrentStep(session)

        assertSame(failed, result.session)
        assertEquals(InteractiveFlowStep.Error, result.step)
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

    // --- helpers ---

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
            purpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = userId
        )
    }
}
