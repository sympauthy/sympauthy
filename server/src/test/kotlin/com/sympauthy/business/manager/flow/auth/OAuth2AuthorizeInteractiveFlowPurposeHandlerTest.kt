package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.UserGrantScopesResult
import com.sympauthy.business.manager.auth.UserScopeGrantingManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.auth.OAuth2AuthorizeInteractiveFlowStatus
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.TerminalEffectResult
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.Scope
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class OAuth2AuthorizeInteractiveFlowPurposeHandlerTest {

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
    lateinit var totpManager: TotpManager

    @MockK
    lateinit var uncheckedFeaturesConfig: EnabledFeaturesConfig

    @MockK
    lateinit var clientManager: ClientManager

    private val testAudience = Audience(id = "test-audience", tokenAudience = "test-audience")
    private val clientManagerProvider: Provider<ClientManager> = Provider { clientManager }

    @SpyK
    @InjectMockKs
    lateinit var handler: OAuth2AuthorizeInteractiveFlowPurposeHandler

    // --- nextStepOrNull ---

    @Test
    fun `nextStepOrNull - Missing user without invitation returns SignIn`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of(invitationId = null)
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(missingUser = true)

        assertEquals(InteractiveFlowStep.SignIn, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Missing user with invitation returns SignUp`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of(invitationId = UUID.randomUUID())
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(missingUser = true)

        assertEquals(InteractiveFlowStep.SignUp, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Missing required claims returns CollectClaims`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(missingRequiredClaims = true)

        assertEquals(InteractiveFlowStep.CollectClaims, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Missing validation media returns ValidateClaims with the first media`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(
            missingMediaForClaimValidation = listOf(ValidationCodeMedia.EMAIL)
        )

        assertEquals(
            InteractiveFlowStep.ValidateClaims(ValidationCodeMedia.EMAIL),
            handler.nextStepOrNull(session)
        )
    }

    @Test
    fun `nextStepOrNull - Returns null once every own step is satisfied`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus()

        assertNull(handler.nextStepOrNull(session))
    }

    // --- followUpPurposes ---

    @Test
    fun `followUpPurposes - Returns the required MFA purpose when none is present`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { purposes } returns listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        }
        coEvery { handler.requiredMfaPurpose(session) } returns InteractiveFlowPurpose.MFA_CHALLENGE

        assertEquals(listOf(InteractiveFlowPurpose.MFA_CHALLENGE), handler.followUpPurposes(session))
    }

    @Test
    fun `followUpPurposes - Empty when no MFA purpose is required`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { purposes } returns listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE)
        }
        coEvery { handler.requiredMfaPurpose(session) } returns null

        assertTrue(handler.followUpPurposes(session).isEmpty())
    }

    @Test
    fun `followUpPurposes - Empty when an MFA purpose is already present`() = runTest {
        // requiredMfaPurpose is left unstubbed: reaching the assertion proves the handler never consulted it
        // because an MFA purpose is already in the list.
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { purposes } returns listOf(
                InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
                InteractiveFlowPurpose.MFA_CHALLENGE
            )
        }

        assertTrue(handler.followUpPurposes(session).isEmpty())
    }

    // --- requiredMfaPurpose ---

    @Test
    fun `requiredMfaPurpose - Returns null when MFA is disabled`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        every { uncheckedMfaConfig.enabled } returns false

        assertNull(handler.requiredMfaPurpose(session))
    }

    @Test
    fun `requiredMfaPurpose - Returns MFA_CHALLENGE when the user is enrolled`() = runTest {
        val userId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> { every { this@mockk.userId } returns userId }
        every { uncheckedMfaConfig.enabled } returns true
        coEvery { totpManager.isEnrolled(userId) } returns true

        assertEquals(InteractiveFlowPurpose.MFA_CHALLENGE, handler.requiredMfaPurpose(session))
    }

    @Test
    fun `requiredMfaPurpose - Returns MFA_ENROLLMENT on sign-up when not enrolled`() = runTest {
        val userId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { signedUp } returns true
        }
        every { uncheckedMfaConfig.enabled } returns true
        coEvery { totpManager.isEnrolled(userId) } returns false

        assertEquals(InteractiveFlowPurpose.MFA_ENROLLMENT, handler.requiredMfaPurpose(session))
    }

    @Test
    fun `requiredMfaPurpose - Returns MFA_ENROLLMENT on sign-in when required and not enrolled`() = runTest {
        val userId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { signedUp } returns false
        }
        every { uncheckedMfaConfig.enabled } returns true
        every { uncheckedMfaConfig.required } returns true
        coEvery { totpManager.isEnrolled(userId) } returns false

        assertEquals(InteractiveFlowPurpose.MFA_ENROLLMENT, handler.requiredMfaPurpose(session))
    }

    @Test
    fun `requiredMfaPurpose - Returns null on sign-in when not enrolled and not required`() = runTest {
        val userId = UUID.randomUUID()
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { signedUp } returns false
        }
        every { uncheckedMfaConfig.enabled } returns true
        every { uncheckedMfaConfig.required } returns false
        coEvery { totpManager.isEnrolled(userId) } returns false

        assertNull(handler.requiredMfaPurpose(session))
    }

    // --- applyTerminalEffect ---

    @Test
    fun `applyTerminalEffect - Saves consent and proceeds when scopes are granted`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val grantedScopeObjects = listOf(mockkScope("read"))
        val session = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(clientId = clientId, grantedScopes = listOf("read"))

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns false
        coEvery { collectedClaimManager.findByUserId(userId) } returns emptyList()
        coEvery { scopeGrantingManager.grantScopes(session, emptyList()) } returns grantScopesResultOf(grantedScopeObjects)
        coEvery { oauth2Manager.setGrantedScopes(session, grantedScopeObjects, any()) } returns oauth2AfterGranted
        coEvery { clientManager.findClientById(clientId) } returns mockClient(clientId)
        coEvery { consentManager.saveConsent(userId, any(), clientId, any()) } returns mockk()

        val result = handler.applyTerminalEffect(session)

        assertEquals(TerminalEffectResult.Proceed, result)
        coVerify(exactly = 1) { consentManager.saveConsent(userId, testAudience.id, clientId, any()) }
    }

    @Test
    fun `applyTerminalEffect - Fails when no scope granted and access without scope is disallowed`() = runTest {
        val userId = UUID.randomUUID()
        val session = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(clientId = "client-id", grantedScopes = emptyList(), consentedScopes = emptyList())

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns false
        coEvery { collectedClaimManager.findByUserId(userId) } returns emptyList()
        coEvery { scopeGrantingManager.grantScopes(session, emptyList()) } returns grantScopesResultOf(emptyList())
        coEvery { oauth2Manager.setGrantedScopes(session, emptyList(), any()) } returns oauth2AfterGranted

        val result = handler.applyTerminalEffect(session)

        val fail = assertInstanceOf(TerminalEffectResult.Fail::class.java, result)
        assertEquals("flow.authorization_flow.complete.no_scope", fail.error.detailsId)
    }

    @Test
    fun `applyTerminalEffect - Proceeds when no scope granted but access without scope is allowed`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val session = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(clientId = clientId, grantedScopes = emptyList(), consentedScopes = emptyList())

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns true
        coEvery { collectedClaimManager.findByUserId(userId) } returns emptyList()
        coEvery { scopeGrantingManager.grantScopes(session, emptyList()) } returns grantScopesResultOf(emptyList())
        coEvery { oauth2Manager.setGrantedScopes(session, emptyList(), any()) } returns oauth2AfterGranted
        coEvery { clientManager.findClientById(clientId) } returns mockClient(clientId)
        coEvery { consentManager.saveConsent(userId, any(), clientId, any()) } returns mockk()

        val result = handler.applyTerminalEffect(session)

        assertEquals(TerminalEffectResult.Proceed, result)
    }

    // --- computeStatus (status computation used by nextStepOrNull) ---

    @Test
    fun `computeStatus - Missing required claims when not all are collected`() = runTest {
        val userId = UUID.randomUUID()
        val session = onGoingSessionMock(userId)
        val oauth2 = stubClaims(session, userId, allRequiredCollected = false)

        assertTrue(handler.computeStatus(session, oauth2).missingRequiredClaims)
    }

    @Test
    fun `computeStatus - Missing validation media when a claim needs validation`() = runTest {
        val userId = UUID.randomUUID()
        val session = onGoingSessionMock(userId)
        val oauth2 = stubClaims(
            session, userId, allRequiredCollected = true,
            reasons = listOf(ValidationCodeReason.EMAIL_CLAIM)
        )

        assertTrue(handler.computeStatus(session, oauth2).missingMediaForClaimValidation.isNotEmpty())
    }

    // --- helpers ---

    private fun onGoingSessionMock(userId: UUID) = mockk<OnGoingInteractiveFlowSession> {
        every { this@mockk.userId } returns userId
    }

    private fun stubClaims(
        session: OnGoingInteractiveFlowSession,
        userId: UUID,
        allRequiredCollected: Boolean,
        reasons: List<ValidationCodeReason> = emptyList()
    ): InteractiveFlowSessionOAuth2 {
        val consentedScopes = listOf("openid", "profile")
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
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = userId
        )
    }
}
