package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.NonInteractiveAuthorizationFlow
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.model.ClientTemplate
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.EnabledClientTemplatesConfig
import com.sympauthy.config.model.EnabledMfaConfig
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowManagerTest {

    @MockK
    lateinit var authorizationFlowManager: AuthorizationFlowManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var claimValidationManager: WebAuthorizationFlowClaimValidationManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var uncheckedMfaConfig: EnabledMfaConfig

    var uncheckedClientTemplatesConfig: Flow<ClientTemplatesConfig> = flowOf(
        EnabledClientTemplatesConfig(emptyMap())
    )

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
    fun `getStatusForOnGoingSession - Non complete if missing claims`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("openid", "profile")
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { mfaPassed } returns false
        }
        every { uncheckedMfaConfig.enabled } returns false
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2With(consentedScopes = consentedScopes)
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
                userId,
                consentedScopes
            )
        } returns emptyList()
        every {
            consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(
                any(),
                consentedScopes
            )
        } returns false
        coEvery { collectedClaimManager.findIdentifierByUserId(any()) } returns emptyList()
        every { claimValidationManager.getReasonsToSendValidationCode(any(), any()) } returns emptyList()

        val result = manager.getStatusForOnGoingSession(session)

        assertTrue(result.missingRequiredClaims)
    }

    @Test
    fun `getStatusForOnGoingSession - Non complete if missing validation`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("openid", "profile")
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { mfaPassed } returns false
        }
        every { uncheckedMfaConfig.enabled } returns false
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2With(consentedScopes = consentedScopes)
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
                userId,
                consentedScopes
            )
        } returns emptyList()
        every {
            consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(
                any(),
                consentedScopes
            )
        } returns true
        coEvery { collectedClaimManager.findIdentifierByUserId(any()) } returns emptyList()
        every { claimValidationManager.getReasonsToSendValidationCode(any(), any()) } returns listOf(
            ValidationCodeReason.EMAIL_CLAIM,
        )

        val result = manager.getStatusForOnGoingSession(session)

        assertTrue(result.missingMediaForClaimValidation.isNotEmpty())
    }

    @Test
    fun `getStatusForOnGoingSession - Missing MFA when user has not passed MFA and MFA is enabled`() =
        runTest {
            val userId = UUID.randomUUID()
            val consentedScopes = listOf("openid", "profile")
            val session = mockk<OnGoingInteractiveFlowSession> {
                every { this@mockk.userId } returns userId
                every { mfaPassed } returns false
            }
            every { uncheckedMfaConfig.enabled } returns true
            coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2With(consentedScopes = consentedScopes)
            coEvery {
                consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
                    userId,
                    consentedScopes
                )
            } returns emptyList()
            every {
                consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(
                    any(),
                    consentedScopes
                )
            } returns true
            coEvery { collectedClaimManager.findIdentifierByUserId(any()) } returns emptyList()
            every { claimValidationManager.getReasonsToSendValidationCode(any(), any()) } returns emptyList()

            val result = manager.getStatusForOnGoingSession(session)

            assertTrue(result.missingMfa)
        }

    @Test
    fun `getStatusForOnGoingSession - Not missing MFA when user has already passed MFA`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("openid", "profile")
        val session = mockk<OnGoingInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
            every { mfaPassed } returns true
        }
        every { uncheckedMfaConfig.enabled } returns true
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2With(consentedScopes = consentedScopes)
        coEvery {
            consentAwareCollectedClaimManager.findByUserIdAndReadableByClient(
                userId,
                consentedScopes
            )
        } returns emptyList()
        every {
            consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(
                any(),
                consentedScopes
            )
        } returns true
        coEvery { collectedClaimManager.findIdentifierByUserId(any()) } returns emptyList()
        every { claimValidationManager.getReasonsToSendValidationCode(any(), any()) } returns emptyList()

        val result = manager.getStatusForOnGoingSession(session)

        assertFalse(result.missingMfa)
    }

    @Test
    fun `getStatusAndCompleteIfNecessary - Complete`() = runTest {
        val session = mockk<OnGoingInteractiveFlowSession>()
        val completedSession = mockk<CompletedInteractiveFlowSession>()
        val status = mockk<WebAuthorizationFlowStatus> {
            every { complete } returns true
        }

        coEvery { manager.getStatus(session) } returns status
        coEvery {
            authorizationFlowManager.completeAuthorization(session)
        } returns completedSession

        val result = manager.getStatusAndCompleteIfNecessary(session)

        assertSame(completedSession, result.first)
        assertTrue(result.second.complete)
    }

    // --- PKCE parseCodeChallenge tests ---

    @Test
    fun `parseCodeChallenge - Returns challenge and S256 method when both provided`() {
        val client = mockk<Client> { every { `public` } returns false }
        val (challenge, method, error) = manager.parseCodeChallenge(client, "test-challenge", "S256")

        assertEquals("test-challenge", challenge)
        assertEquals(CodeChallengeMethod.S256, method)
        assertNull(error)
    }

    @Test
    fun `parseCodeChallenge - Defaults to S256 when method not provided`() {
        val client = mockk<Client> { every { `public` } returns false }
        val (challenge, method, error) = manager.parseCodeChallenge(client, "test-challenge", null)

        assertEquals("test-challenge", challenge)
        assertEquals(CodeChallengeMethod.S256, method)
        assertNull(error)
    }

    @Test
    fun `parseCodeChallenge - Returns error for unsupported method`() {
        val client = mockk<Client> { every { `public` } returns false }
        val (challenge, method, error) = manager.parseCodeChallenge(client, "test-challenge", "plain")

        assertNull(challenge)
        assertNull(method)
        assertNotNull(error)
        assertEquals("authorize.pkce.unsupported_method", error!!.detailsId)
    }

    @Test
    fun `parseCodeChallenge - Returns error when public client has no code_challenge`() {
        val client = mockk<Client> { every { `public` } returns true }
        val (challenge, method, error) = manager.parseCodeChallenge(client, null, null)

        assertNull(challenge)
        assertNull(method)
        assertNotNull(error)
        assertEquals("authorize.pkce.missing_code_challenge", error!!.detailsId)
    }

    @Test
    fun `parseCodeChallenge - Returns error when confidential client has no code_challenge`() {
        val client = mockk<Client> { every { `public` } returns false }
        val (challenge, method, error) = manager.parseCodeChallenge(client, null, null)

        assertNull(challenge)
        assertNull(method)
        assertNotNull(error)
        assertEquals("authorize.pkce.missing_code_challenge", error!!.detailsId)
    }

    // --- getDefaultWebAuthorizationFlow tests ---

    @Test
    fun `getDefaultWebAuthorizationFlow - Returns template flow when default template has a WebAuthorizationFlow`() =
        runTest {
            val templateFlow = mockk<WebAuthorizationFlow>()
            val template = ClientTemplate(
                id = "default",
                audienceId = null,
                public = null,
                allowedGrantTypes = null,
                authorizationFlow = templateFlow,
                allowedRedirectUris = null,
                allowedScopes = null,
                defaultScopes = null,
                authorizationWebhook = null
            )
            val templatesConfig = EnabledClientTemplatesConfig(mapOf("default" to template))
            val realManager = WebAuthorizationFlowManager(
                authorizationFlowManager, oauth2Manager, collectedClaimManager,
                consentAwareCollectedClaimManager, claimValidationManager, clientManager,
                invitationManager, scopeManager, uncheckedMfaConfig, flowOf(templatesConfig)
            )

            val result = realManager.getDefaultWebAuthorizationFlow()

            assertSame(templateFlow, result)
        }

    @Test
    fun `getDefaultWebAuthorizationFlow - Falls back to hardcoded flow when no default template`() = runTest {
        val hardcodedFlow = mockk<WebAuthorizationFlow>()
        every { authorizationFlowManager.defaultWebAuthorizationFlow } returns hardcodedFlow
        val templatesConfig = EnabledClientTemplatesConfig(emptyMap())
        val realManager = WebAuthorizationFlowManager(
            authorizationFlowManager, oauth2Manager, collectedClaimManager,
            consentAwareCollectedClaimManager, claimValidationManager, clientManager,
            invitationManager, scopeManager, uncheckedMfaConfig, flowOf(templatesConfig)
        )

        val result = realManager.getDefaultWebAuthorizationFlow()

        assertSame(hardcodedFlow, result)
    }

    @Test
    fun `getDefaultWebAuthorizationFlow - Falls back to hardcoded flow when template flow is not WebAuthorizationFlow`() =
        runTest {
            val nonInteractiveFlow = mockk<NonInteractiveAuthorizationFlow>()
            val hardcodedFlow = mockk<WebAuthorizationFlow>()
            every { authorizationFlowManager.defaultWebAuthorizationFlow } returns hardcodedFlow
            val template = ClientTemplate(
                id = "default",
                audienceId = null,
                public = null,
                allowedGrantTypes = null,
                authorizationFlow = nonInteractiveFlow,
                allowedRedirectUris = null,
                allowedScopes = null,
                defaultScopes = null,
                authorizationWebhook = null
            )
            val templatesConfig = EnabledClientTemplatesConfig(mapOf("default" to template))
            val realManager = WebAuthorizationFlowManager(
                authorizationFlowManager, oauth2Manager, collectedClaimManager,
                consentAwareCollectedClaimManager, claimValidationManager, clientManager,
                invitationManager, scopeManager, uncheckedMfaConfig, flowOf(templatesConfig)
            )

            val result = realManager.getDefaultWebAuthorizationFlow()

            assertSame(hardcodedFlow, result)
        }

    @Test
    fun `getDefaultWebAuthorizationFlow - Falls back to hardcoded flow when template has no authorizationFlow`() =
        runTest {
            val hardcodedFlow = mockk<WebAuthorizationFlow>()
            every { authorizationFlowManager.defaultWebAuthorizationFlow } returns hardcodedFlow
            val template = ClientTemplate(
                id = "default",
                audienceId = null,
                public = null,
                allowedGrantTypes = null,
                authorizationFlow = null,
                allowedRedirectUris = null,
                allowedScopes = null,
                defaultScopes = null,
                authorizationWebhook = null
            )
            val templatesConfig = EnabledClientTemplatesConfig(mapOf("default" to template))
            val realManager = WebAuthorizationFlowManager(
                authorizationFlowManager, oauth2Manager, collectedClaimManager,
                consentAwareCollectedClaimManager, claimValidationManager, clientManager,
                invitationManager, scopeManager, uncheckedMfaConfig, flowOf(templatesConfig)
            )

            val result = realManager.getDefaultWebAuthorizationFlow()

            assertSame(hardcodedFlow, result)
        }

    // --- startAuthorizationWith tests ---

    private val defaultFlow = mockk<WebAuthorizationFlow>()

    private fun setupDefaultFlow() {
        coEvery { manager.getDefaultWebAuthorizationFlow() } returns defaultFlow
    }

    private fun setupValidClient(
        client: Client,
        scopes: List<Scope> = emptyList(),
        redirectUri: URI = URI("https://example.com/callback")
    ) {
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery { scopeManager.parseRequestedScopes(client, any()) } returns scopes
        every { manager.parseRequestedRedirectUri(client, any()) } returns redirectUri
    }

    @Test
    fun `startAuthorizationWith - Stores error when client_id is null`() = runTest {
        val clientException = businessExceptionOf(detailsId = "client.parse_requested.missing")
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(null) } throws clientException
        val errorSlot = slot<BusinessException?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = null,
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = captureNullable(errorSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = null,
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = null
        )

        assertEquals("client.parse_requested.missing", errorSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Stores error when client_id is unknown`() = runTest {
        val clientException = businessExceptionOf(detailsId = "client.invalid_client_id")
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient("unknown") } throws clientException
        val errorSlot = slot<BusinessException?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = null,
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = captureNullable(errorSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "unknown",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = null
        )

        assertEquals("client.invalid_client_id", errorSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Uses default flow when client has no authorizationFlow`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns false
            every { supportsGrantType(any()) } returns true
        }
        setupDefaultFlow()
        setupValidClient(client)
        val flowSlot = slot<WebAuthorizationFlow>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = any(),
                flow = capture(flowSlot),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = any()
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "client",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = "https://example.com/callback"
        )

        assertSame(defaultFlow, flowSlot.captured)
    }

    @Test
    fun `startAuthorizationWith - Stores scope error when scope is invalid`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns false
            every { supportsGrantType(any()) } returns true
        }
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery {
            scopeManager.parseRequestedScopes(
                client,
                any()
            )
        } throws businessExceptionOf(detailsId = "scope.unsupported")
        every { manager.parseRequestedRedirectUri(client, any()) } returns URI("https://example.com/callback")
        val errorSlot = slot<BusinessException?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = captureNullable(errorSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "client",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = "invalid_scope",
            uncheckedRedirectUri = "https://example.com/callback"
        )

        assertEquals("scope.unsupported", errorSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Skips scope validation when client is null`() = runTest {
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(null) } throws businessExceptionOf(detailsId = "client.parse_requested.missing")
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = any()
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = null,
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = "openid",
            uncheckedRedirectUri = null
        )

        coVerify(exactly = 0) { scopeManager.parseRequestedScopes(any(), any()) }
    }

    @Test
    fun `startAuthorizationWith - Stores redirect_uri error when redirect_uri is blank`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns false
            every { supportsGrantType(any()) } returns true
        }
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery { scopeManager.parseRequestedScopes(client, any()) } returns emptyList()
        every { manager.parseRequestedRedirectUri(client, any()) } throws businessExceptionOf(
            detailsId = "flow.web.parse_requested_redirect_uri.missing"
        )
        val errorSlot = slot<BusinessException?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = captureNullable(errorSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "client",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = ""
        )

        assertEquals("flow.web.parse_requested_redirect_uri.missing", errorSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Skips redirect_uri validation when client is null`() = runTest {
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(null) } throws businessExceptionOf(detailsId = "client.parse_requested.missing")
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = any()
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = null,
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = "https://example.com/callback"
        )

        verify(exactly = 0) { manager.parseRequestedRedirectUri(any(), any()) }
    }

    @Test
    fun `startAuthorizationWith - Stores PKCE error for public client without code_challenge`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns true
            every { supportsGrantType(any()) } returns true
        }
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery { scopeManager.parseRequestedScopes(client, any()) } returns emptyList()
        every { manager.parseRequestedRedirectUri(client, any()) } returns URI("https://example.com/callback")
        val errorSlot = slot<BusinessException?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = captureNullable(errorSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "public-client",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = "https://example.com/callback",
            uncheckedCodeChallenge = null,
            uncheckedCodeChallengeMethod = null
        )

        assertEquals("authorize.pkce.missing_code_challenge", errorSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Stores code_challenge and S256 on valid PKCE request`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns false
            every { supportsGrantType(any()) } returns true
        }
        setupDefaultFlow()
        setupValidClient(client)
        val challengeSlot = slot<String?>()
        val methodSlot = slot<CodeChallengeMethod?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = captureNullable(challengeSlot),
                codeChallengeMethod = captureNullable(methodSlot),
                invitationId = any(),
                error = any()
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "client",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = "https://example.com/callback",
            uncheckedCodeChallenge = "my-challenge",
            uncheckedCodeChallengeMethod = "S256"
        )

        assertEquals("my-challenge", challengeSlot.captured)
        assertEquals(CodeChallengeMethod.S256, methodSlot.captured)
    }

    @Test
    fun `startAuthorizationWith - Client error takes priority over other errors`() = runTest {
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(null) } throws businessExceptionOf(detailsId = "client.parse_requested.missing")
        val errorSlot = slot<BusinessException?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = captureNullable(errorSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = null,
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = "invalid",
            uncheckedRedirectUri = ""
        )

        assertEquals("client.parse_requested.missing", errorSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Uses first error when multiple validations fail`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns true
            every { supportsGrantType(any()) } returns true
        }
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery {
            scopeManager.parseRequestedScopes(
                client,
                any()
            )
        } throws businessExceptionOf(detailsId = "scope.unsupported")
        every { manager.parseRequestedRedirectUri(client, any()) } throws businessExceptionOf(
            detailsId = "flow.web.parse_requested_redirect_uri.missing"
        )
        val errorSlot = slot<BusinessException?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = captureNullable(errorSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "client",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = "invalid",
            uncheckedRedirectUri = ""
        )

        // Scope error comes before redirect_uri error in the listOfNotNull
        assertEquals("scope.unsupported", errorSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Passes state to startOAuth2Session`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns false
            every { supportsGrantType(any()) } returns true
        }
        setupDefaultFlow()
        setupValidClient(client)
        val stateSlot = slot<String?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = captureNullable(stateSlot),
                clientNonce = any(),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = any()
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "client",
            uncheckedClientState = "my-state-value",
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = "https://example.com/callback"
        )

        assertEquals("my-state-value", stateSlot.captured)
    }

    @Test
    fun `startAuthorizationWith - Passes nonce to startOAuth2Session`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns false
            every { supportsGrantType(any()) } returns true
        }
        setupDefaultFlow()
        setupValidClient(client)
        val nonceSlot = slot<String?>()
        coEvery {
            oauth2Manager.startOAuth2Session(
                client = any(),
                clientState = any(),
                clientNonce = captureNullable(nonceSlot),
                flow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                invitationId = any(),
                error = any()
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "client",
            uncheckedClientState = null,
            uncheckedClientNonce = "my-nonce-value",
            uncheckedScopes = null,
            uncheckedRedirectUri = "https://example.com/callback"
        )

        assertEquals("my-nonce-value", nonceSlot.captured)
    }

    // --- parseRequestedRedirectUri tests ---

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is null`() {
        val client = mockk<Client>()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, null)
        }
        assertEquals("flow.web.parse_requested_redirect_uri.missing", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is blank`() {
        val client = mockk<Client>()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "   ")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.missing", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is not a valid URI`() {
        val client = mockk<Client>()
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "://not-valid")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.invalid", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Throws when redirect_uri is not in allowedRedirectUris`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://allowed.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://other.com/callback")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Accepts redirect_uri matching an allowed URI exactly`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val result = manager.parseRequestedRedirectUri(client, "https://example.com/callback")
        assertEquals(URI("https://example.com/callback"), result)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different path`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://example.com/other-path")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with extra query params`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://example.com/callback?extra=param")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different case in scheme`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "HTTPS://example.com/callback")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different case in host`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf("https://example.com/callback")
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://Example.com/callback")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.not_allowed", exception.detailsId)
    }

    // --- matchesAllowedRedirectUri loopback tests ---

    @Test
    fun `matchesAllowedRedirectUri - Allows different port on 127_0_0_1 loopback`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://127.0.0.1:12345/callback",
            listOf("http://127.0.0.1:8080/callback")
        )
        assertTrue(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Allows different port on IPv6 loopback`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://[::1]:12345/callback",
            listOf("http://[::1]:8080/callback")
        )
        assertTrue(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Rejects different port on localhost`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://localhost:12345/callback",
            listOf("http://localhost:8080/callback")
        )
        assertFalse(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Rejects loopback with different path`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://127.0.0.1:12345/other",
            listOf("http://127.0.0.1:8080/callback")
        )
        assertFalse(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Rejects cross-family loopback mismatch`() {
        val result = manager.matchesAllowedRedirectUri(
            "http://127.0.0.1:12345/callback",
            listOf("http://[::1]:8080/callback")
        )
        assertFalse(result)
    }

    @Test
    fun `matchesAllowedRedirectUri - Rejects loopback flexibility for custom scheme`() {
        val result = manager.matchesAllowedRedirectUri(
            "myapp://127.0.0.1:12345/callback",
            listOf("myapp://127.0.0.1:8080/callback")
        )
        assertFalse(result)
    }

    // --- checkSignUpAllowed ---

    private fun createSession(): OnGoingInteractiveFlowSession = mockk()

    private fun mockOAuth2AndClient(
        session: OnGoingInteractiveFlowSession,
        clientId: String = "test-client",
        invitationId: UUID? = null,
        signUpEnabled: Boolean = true,
        invitationEnabled: Boolean = false
    ) {
        coEvery { oauth2Manager.fetchOAuth2(session) } returns oauth2With(
            clientId = clientId,
            invitationId = invitationId
        )
        val audience = Audience(
            id = "test-audience",
            tokenAudience = "test-audience",
            signUpEnabled = signUpEnabled,
            invitationEnabled = invitationEnabled
        )
        val client = mockk<Client> {
            every { this@mockk.audience } returns audience
        }
        coEvery { clientManager.findClientById(clientId) } returns client
    }

    @Test
    fun `checkSignUpAllowed - Succeeds when sign-up is enabled`() = runTest {
        val session = createSession()
        mockOAuth2AndClient(session, signUpEnabled = true, invitationEnabled = false)
        manager.checkSignUpAllowed(session, recoverable = true)
    }

    @Test
    fun `checkSignUpAllowed - Succeeds when both sign-up and invitation enabled without invitation`() = runTest {
        val session = createSession()
        mockOAuth2AndClient(session, invitationId = null, signUpEnabled = true, invitationEnabled = true)
        manager.checkSignUpAllowed(session, recoverable = true)
    }

    @Test
    fun `checkSignUpAllowed - Succeeds when invitation required and invitation is bound`() = runTest {
        val session = createSession()
        mockOAuth2AndClient(
            session,
            invitationId = UUID.randomUUID(),
            signUpEnabled = false,
            invitationEnabled = true
        )
        manager.checkSignUpAllowed(session, recoverable = false)
    }

    @Test
    fun `checkSignUpAllowed - Throws when both sign-up and invitation are disabled`() = runTest {
        val session = createSession()
        mockOAuth2AndClient(session, signUpEnabled = false, invitationEnabled = false)

        val exception = assertThrows<BusinessException> {
            manager.checkSignUpAllowed(session, recoverable = true)
        }
        assertEquals("flow.sign_up.disabled", exception.detailsId)
    }

    @Test
    fun `checkSignUpAllowed - Throws when invitation required but not bound`() = runTest {
        val session = createSession()
        mockOAuth2AndClient(session, invitationId = null, signUpEnabled = false, invitationEnabled = true)

        val exception = assertThrows<BusinessException> {
            manager.checkSignUpAllowed(session, recoverable = false)
        }
        assertEquals("flow.sign_up.invitation_required", exception.detailsId)
    }

    @Test
    fun `checkSignUpAllowed - Respects recoverable flag`() = runTest {
        val session = createSession()
        mockOAuth2AndClient(session, signUpEnabled = false, invitationEnabled = false)

        val recoverableException = assertThrows<BusinessException> {
            manager.checkSignUpAllowed(session, recoverable = true)
        }
        assertTrue(recoverableException.recoverable)

        val nonRecoverableException = assertThrows<BusinessException> {
            manager.checkSignUpAllowed(session, recoverable = false)
        }
        assertFalse(nonRecoverableException.recoverable)
    }

    private fun oauth2With(
        clientId: String = "test-client",
        consentedScopes: List<String>? = null,
        invitationId: UUID? = null
    ): InteractiveFlowSessionOAuth2 = InteractiveFlowSessionOAuth2(
        sessionId = UUID.randomUUID(),
        clientId = clientId,
        redirectUri = "https://example.com/callback",
        requestedScopes = emptyList(),
        consentedScopes = consentedScopes,
        invitationId = invitationId
    )
}
