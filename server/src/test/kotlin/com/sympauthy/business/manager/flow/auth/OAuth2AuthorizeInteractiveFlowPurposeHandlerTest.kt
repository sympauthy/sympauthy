package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.UserGrantScopesResult
import com.sympauthy.business.manager.auth.UserScopeGrantingManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.code.ValidationCodeMedia
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.auth.OAuth2AuthorizeInteractiveFlowStatus
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.TerminalEffectResult
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.ConsentedBy
import com.sympauthy.business.model.oauth2.EnabledScope
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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertFalse
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
    lateinit var invitationManager: InvitationManager

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

    @Test
    fun `nextStepOrNull - Missing user without invitation returns SignIn`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of(invitationId = null)
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(
            missingUser = true
        )

        assertEquals(InteractiveFlowStep.SignIn, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Missing user with invitation returns SignUp`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of(invitationId = UUID.randomUUID())
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(
            missingUser = true
        )

        assertEquals(InteractiveFlowStep.SignUp, handler.nextStepOrNull(session))
    }

    @Test
    fun `nextStepOrNull - Missing required claims returns CollectClaims`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2Of()
        coEvery { handler.computeStatus(session, any()) } returns OAuth2AuthorizeInteractiveFlowStatus(
            missingRequiredClaims = true
        )

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

    @Test
    fun `applyTerminalEffect - Saves consent and proceeds when scopes are granted`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val grantedScopeObjects = listOf(mockk<EnabledScope>())
        val session = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(clientId = clientId, grantedScopes = listOf("read"))

        val oauth2 = stubAudienceClaims(session, userId, emptyList())
        coEvery { scopeGrantingManager.grantScopes(session, oauth2, emptyList()) } returns grantScopesResultOf(
            grantedScopeObjects
        )
        coEvery { oauth2Manager.setGrantedScopes(session, grantedScopeObjects, any()) } returns oauth2AfterGranted
        coEvery { consentManager.saveConsent(userId, testAudience.id, clientId, any()) } returns mockk()

        val result = handler.applyTerminalEffect(session)

        assertEquals(TerminalEffectResult.Proceed, result)
    }

    @Test
    fun `applyTerminalEffect - Consumes the invitation that brought the end-user here`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val invitationId = UUID.randomUUID()
        val grantedScopeObjects = listOf(mockk<EnabledScope>())
        val session = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(
            clientId = clientId,
            grantedScopes = listOf("read"),
            invitationId = invitationId
        )

        val oauth2 = stubAudienceClaims(session, userId, emptyList())
        coEvery { scopeGrantingManager.grantScopes(session, oauth2, emptyList()) } returns grantScopesResultOf(
            grantedScopeObjects
        )
        coEvery { oauth2Manager.setGrantedScopes(session, grantedScopeObjects, any()) } returns oauth2AfterGranted
        coEvery { consentManager.saveConsent(userId, testAudience.id, clientId, any()) } returns mockk()
        coEvery { invitationManager.consumeInvitation(invitationId, userId) } returns mockk()

        val result = handler.applyTerminalEffect(session)

        assertEquals(TerminalEffectResult.Proceed, result)
        coVerify { invitationManager.consumeInvitation(invitationId, userId) }
    }

    @Test
    fun `applyTerminalEffect - Fails when no scope granted and access without scope is disallowed`() = runTest {
        val userId = UUID.randomUUID()
        val session = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(
            clientId = "client-id",
            grantedScopes = emptyList(),
            consentedScopes = emptyList()
        )

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns false
        val oauth2 = stubAudienceClaims(session, userId, emptyList())
        coEvery { scopeGrantingManager.grantScopes(session, oauth2, emptyList()) } returns
                grantScopesResultOf(emptyList())
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
        val oauth2AfterGranted = oauth2Of(
            clientId = clientId,
            grantedScopes = emptyList(),
            consentedScopes = emptyList()
        )

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns true
        val oauth2 = stubAudienceClaims(session, userId, emptyList())
        coEvery { scopeGrantingManager.grantScopes(session, oauth2, emptyList()) } returns
                grantScopesResultOf(emptyList())
        coEvery { oauth2Manager.setGrantedScopes(session, emptyList(), any()) } returns oauth2AfterGranted
        coEvery { consentManager.saveConsent(userId, any(), clientId, any()) } returns mockk()

        val result = handler.applyTerminalEffect(session)

        assertEquals(TerminalEffectResult.Proceed, result)
    }

    @Test
    fun `applyTerminalEffect - Read the claims of the audience the authorization is for`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val session = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(clientId = clientId, grantedScopes = listOf("read"))
        val audienceClaims = listOf(mockk<CollectedClaim>())

        val oauth2 = stubAudienceClaims(session, userId, audienceClaims)
        coEvery { scopeGrantingManager.grantScopes(session, oauth2, audienceClaims) } returns
                grantScopesResultOf(emptyList())
        coEvery { oauth2Manager.setGrantedScopes(session, emptyList(), any()) } returns oauth2AfterGranted
        coEvery { consentManager.saveConsent(userId, testAudience.id, clientId, any()) } returns mockk()

        assertEquals(TerminalEffectResult.Proceed, handler.applyTerminalEffect(session))
    }

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


    @Test
    fun `debugInformation - Names the client, the request and the decisions taken on it`() = runTest {
        val session = oauth2SessionMock()
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns oauth2Of(
            clientId = "web-app",
            requestedScopes = listOf("openid", "profile"),
            consentedScopes = listOf("openid")
        )

        val information = handler.debugInformation(session).associate { it.displayName to it.value }

        assertEquals("web-app", information["Client"])
        assertEquals("https://example.com/callback", information["Redirect URI"])
        assertEquals("openid profile", information["Requested scopes"])
        assertEquals("openid", information["Consented scopes"])
        assertEquals("auto", information["Consented by"])
    }

    @Test
    fun `debugInformation - Reports the state and the nonce as present rather than publishing them`() = runTest {
        val session = oauth2SessionMock()
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns oauth2Of(
            state = "a-state-only-the-client-holds",
            nonce = "a-nonce-only-the-browser-holds"
        )

        val information = handler.debugInformation(session)
        val emitted = information.mapNotNull { it.value }

        assertEquals("present", information.first { it.displayName == "State" }.value)
        assertEquals("present", information.first { it.displayName == "Nonce" }.value)
        assertFalse(emitted.any { it.contains("a-state-only-the-client-holds") })
        assertFalse(emitted.any { it.contains("a-nonce-only-the-browser-holds") })
    }

    @Test
    fun `debugInformation - Reports an absent state and nonce as absent`() = runTest {
        val session = oauth2SessionMock()
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns oauth2Of(state = null, nonce = null)

        val information = handler.debugInformation(session)

        assertEquals("absent", information.first { it.displayName == "State" }.value)
        assertEquals("absent", information.first { it.displayName == "Nonce" }.value)
    }

    @Test
    fun `debugInformation - Reduces the code challenge to its method`() = runTest {
        val session = oauth2SessionMock()
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns oauth2Of(
            codeChallenge = "a-challenge-nobody-needs-to-read",
            codeChallengeMethod = CodeChallengeMethod.S256
        )

        val information = handler.debugInformation(session)

        assertEquals("present (S256)", information.first { it.displayName == "Code challenge" }.value)
        assertFalse(
            information.mapNotNull { it.value }.any { it.contains("a-challenge-nobody-needs-to-read") }
        )
    }

    @Test
    fun `debugInformation - Emits every label with no value when the session carries no OAuth2 record`() =
        runTest {
            val session = oauth2SessionMock()
            coEvery { oauth2Manager.fetchOAuth2OrNull(session) } returns null

            val information = handler.debugInformation(session)

            assertTrue(information.isNotEmpty())
            assertTrue(information.all { it.value == null })
        }

    @Test
    fun `debugInformation - Answers for a request that failed validation and never knew its client`() = runTest {
        // The row is written so the session can carry its error, with no client and no redirect URI; the
        // model admits neither as null, so reading it back is an internal failure rather than an absence.
        val session = oauth2SessionMock()
        coEvery { oauth2Manager.fetchOAuth2OrNull(session) } throws internalBusinessExceptionOf(
            "mapper.interactive_flow_session_oauth2.invalid_property",
            "property" to "clientId"
        )

        val information = handler.debugInformation(session)

        assertTrue(information.isNotEmpty())
        assertTrue(information.all { it.value == null })
    }

    @Test
    fun `debugInformation - Answers without reading the record when the session was started by another purpose`() =
        runTest {
            // oauth2Manager is left unstubbed: reaching the assertion proves the read the fetch would have
            // refused was never made.
            val session = mockk<FailedInteractiveFlowSession> {
                every { initiatingPurpose } returns InteractiveFlowPurpose.MFA_ENROLLMENT
            }

            assertTrue(handler.debugInformation(session).all { it.value == null })
        }


    /**
     * The record the audience is resolved from before anything is granted, and the read made with it. Nothing
     * stubs `findByUserId`, so a handler reading the claims unnarrowed reaches no stub at all.
     */
    private fun stubAudienceClaims(
        session: OnGoingInteractiveFlowSession,
        userId: UUID,
        audienceClaims: List<CollectedClaim>,
    ): InteractiveFlowSessionOAuth2 {
        val oauth2 = oauth2Of(clientId = "client-id")
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2
        coEvery { oauth2Manager.getAudienceId(oauth2) } returns testAudience.id
        coEvery { collectedClaimManager.findByUserIdAndAudience(userId, testAudience.id) } returns audienceClaims
        return oauth2
    }

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
        val oauth2 = oauth2Of(consentedScopes = consentedScopes)
        coEvery { oauth2Manager.getAudienceId(oauth2) } returns testAudience.id
        coEvery { collectedClaimManager.findIdentifierByUserId(userId) } returns emptyList()
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
                userId, testAudience.id, consentedScopes
            )
        } returns emptyList()
        every {
            consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(
                any(), testAudience.id, consentedScopes
            )
        } returns allRequiredCollected
        every { claimValidationManager.getReasonsToSendValidationCode(any(), any(), any()) } returns reasons
        return oauth2
    }

    private fun oauth2Of(
        clientId: String = "client-id",
        consentedScopes: List<String>? = null,
        grantedScopes: List<String>? = null,
        invitationId: UUID? = null,
        requestedScopes: List<String> = emptyList(),
        state: String? = "state",
        nonce: String? = "nonce",
        codeChallenge: String? = null,
        codeChallengeMethod: CodeChallengeMethod? = null,
    ): InteractiveFlowSessionOAuth2 {
        return InteractiveFlowSessionOAuth2(
            sessionId = UUID.randomUUID(),
            clientId = clientId,
            redirectUri = "https://example.com/callback",
            requestedScopes = requestedScopes,
            state = state,
            nonce = nonce,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod,
            consentedScopes = consentedScopes,
            consentedAt = consentedScopes?.let { LocalDateTime.now() },
            consentedBy = consentedScopes?.let { ConsentedBy.AUTO },
            grantedScopes = grantedScopes,
            invitationId = invitationId,
        )
    }

    private fun grantScopesResultOf(grantedScopes: List<EnabledScope>): UserGrantScopesResult {
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

    private fun oauth2SessionMock() = mockk<OnGoingInteractiveFlowSession> {
        every { initiatingPurpose } returns InteractiveFlowPurpose.OAUTH2_AUTHORIZE
    }

    private fun createOnGoingSession(userId: UUID?): OnGoingInteractiveFlowSession {
        return OnGoingInteractiveFlowSession(
            id = UUID.randomUUID(),
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            initiatingClientId = null,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = userId
        )
    }
}
