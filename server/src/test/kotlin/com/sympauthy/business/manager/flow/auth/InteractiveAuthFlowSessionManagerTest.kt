package com.sympauthy.business.manager.flow.auth

import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.NonInteractiveAuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.business.model.oauth2.CodeChallengeMethod
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.model.ClientTemplate
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.EnabledClientTemplatesConfig
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
class InteractiveAuthFlowSessionManagerTest {

    @MockK
    lateinit var authorizationFlowManager: AuthorizationFlowManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var invitationManager: InvitationManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var clientRedirectUriManager: ClientRedirectUriManager

    var uncheckedClientTemplatesConfig: Flow<ClientTemplatesConfig> = flowOf(
        EnabledClientTemplatesConfig(emptyMap())
    )

    @SpyK
    @InjectMockKs
    lateinit var manager: InteractiveAuthFlowSessionManager

    @Test
    fun `findByIdOrNull - Returns InteractiveFlow when found`() {
        val flowId = "test-flow-id"
        val webFlow = mockk<InteractiveFlow>()

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
    fun `findByIdOrNull - Returns null when flow is not a InteractiveFlow`() {
        val flowId = "non-web-flow-id"
        val nonWebFlow = mockk<NonInteractiveAuthorizationFlow>()

        every { authorizationFlowManager.findByIdOrNull(flowId) } returns nonWebFlow

        val result = manager.findByIdOrNull(flowId)

        assertNull(result)
    }

    @Test
    fun `findById - Returns InteractiveFlow when found`() {
        val flowId = "test-flow-id"
        val webFlow = mockk<InteractiveFlow>()

        every { authorizationFlowManager.findByIdOrNull(flowId) } returns webFlow

        val result = manager.findById(flowId)

        assertEquals(webFlow, result)
    }

    @Test
    fun `findById - Throws BusinessException when flow is not found or not a InteractiveFlow`() {
        val flowId = "non-existent-flow-id"

        every { authorizationFlowManager.findByIdOrNull(flowId) } returns null

        val exception = assertThrows<BusinessException> {
            manager.findById(flowId)
        }

        assertEquals("flow.web.invalid_flow", exception.detailsId)
        assertFalse(exception.recoverable)
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

    // --- getDefaultInteractiveFlow tests ---

    @Test
    fun `getDefaultInteractiveFlow - Returns template flow when default template has a InteractiveFlow`() =
        runTest {
            val templateFlow = mockk<InteractiveFlow>()
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
            val realManager = InteractiveAuthFlowSessionManager(
                authorizationFlowManager, oauth2Manager, clientManager,
                invitationManager, scopeManager, clientRedirectUriManager, flowOf(templatesConfig)
            )

            val result = realManager.getDefaultInteractiveFlow()

            assertSame(templateFlow, result)
        }

    @Test
    fun `getDefaultInteractiveFlow - Falls back to hardcoded flow when no default template`() = runTest {
        val hardcodedFlow = mockk<InteractiveFlow>()
        every { authorizationFlowManager.defaultInteractiveFlow } returns hardcodedFlow
        val templatesConfig = EnabledClientTemplatesConfig(emptyMap())
        val realManager = InteractiveAuthFlowSessionManager(
            authorizationFlowManager, oauth2Manager, clientManager,
            invitationManager, scopeManager, clientRedirectUriManager, flowOf(templatesConfig)
        )

        val result = realManager.getDefaultInteractiveFlow()

        assertSame(hardcodedFlow, result)
    }

    @Test
    fun `getDefaultInteractiveFlow - Falls back to hardcoded flow when template flow is not InteractiveFlow`() =
        runTest {
            val nonInteractiveFlow = mockk<NonInteractiveAuthorizationFlow>()
            val hardcodedFlow = mockk<InteractiveFlow>()
            every { authorizationFlowManager.defaultInteractiveFlow } returns hardcodedFlow
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
            val realManager = InteractiveAuthFlowSessionManager(
                authorizationFlowManager, oauth2Manager, clientManager,
                invitationManager, scopeManager, clientRedirectUriManager, flowOf(templatesConfig)
            )

            val result = realManager.getDefaultInteractiveFlow()

            assertSame(hardcodedFlow, result)
        }

    @Test
    fun `getDefaultInteractiveFlow - Falls back to hardcoded flow when template has no authorizationFlow`() =
        runTest {
            val hardcodedFlow = mockk<InteractiveFlow>()
            every { authorizationFlowManager.defaultInteractiveFlow } returns hardcodedFlow
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
            val realManager = InteractiveAuthFlowSessionManager(
                authorizationFlowManager, oauth2Manager, clientManager,
                invitationManager, scopeManager, clientRedirectUriManager, flowOf(templatesConfig)
            )

            val result = realManager.getDefaultInteractiveFlow()

            assertSame(hardcodedFlow, result)
        }

    // --- startAuthorizationWith tests ---

    private val defaultFlow = mockk<InteractiveFlow>()

    private fun setupDefaultFlow() {
        coEvery { manager.getDefaultInteractiveFlow() } returns defaultFlow
    }

    private fun setupValidClient(
        client: Client,
        scopes: List<Scope> = emptyList(),
        redirectUri: URI = URI("https://example.com/callback")
    ) {
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery { scopeManager.parseRequestedScopes(client, any()) } returns scopes
        every { clientRedirectUriManager.parseRequestedRedirectUri(client, any(), any()) } returns redirectUri
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
        val flowSlot = slot<InteractiveFlow>()
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
        every { clientRedirectUriManager.parseRequestedRedirectUri(client, any(), any()) } returns URI("https://example.com/callback")
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
        every { clientRedirectUriManager.parseRequestedRedirectUri(client, any(), any()) } throws businessExceptionOf(
            detailsId = "client.redirect_uri.missing"
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

        assertEquals("client.redirect_uri.missing", errorSlot.captured?.detailsId)
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

        verify(exactly = 0) { clientRedirectUriManager.parseRequestedRedirectUri(any(), any(), any()) }
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
        every { clientRedirectUriManager.parseRequestedRedirectUri(client, any(), any()) } returns URI("https://example.com/callback")
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
        every { clientRedirectUriManager.parseRequestedRedirectUri(client, any(), any()) } throws businessExceptionOf(
            detailsId = "client.redirect_uri.missing"
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

    // --- checkSignUpAllowed ---

    private fun oauth2AndClient(
        clientId: String = "test-client",
        invitationId: UUID? = null,
        signUpEnabled: Boolean = true,
        invitationEnabled: Boolean = false
    ): InteractiveFlowSessionOAuth2 {
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
        return oauth2With(clientId = clientId, invitationId = invitationId)
    }

    @Test
    fun `checkSignUpAllowed - Succeeds when sign-up is enabled`() = runTest {
        val oauth2 = oauth2AndClient(signUpEnabled = true, invitationEnabled = false)
        manager.checkSignUpAllowed(oauth2, recoverable = true)
    }

    @Test
    fun `checkSignUpAllowed - Succeeds when both sign-up and invitation enabled without invitation`() = runTest {
        val oauth2 = oauth2AndClient(invitationId = null, signUpEnabled = true, invitationEnabled = true)
        manager.checkSignUpAllowed(oauth2, recoverable = true)
    }

    @Test
    fun `checkSignUpAllowed - Succeeds when invitation required and invitation is bound`() = runTest {
        val oauth2 = oauth2AndClient(
            invitationId = UUID.randomUUID(),
            signUpEnabled = false,
            invitationEnabled = true
        )
        manager.checkSignUpAllowed(oauth2, recoverable = false)
    }

    @Test
    fun `checkSignUpAllowed - Throws when both sign-up and invitation are disabled`() = runTest {
        val oauth2 = oauth2AndClient(signUpEnabled = false, invitationEnabled = false)

        val exception = assertThrows<BusinessException> {
            manager.checkSignUpAllowed(oauth2, recoverable = true)
        }
        assertEquals("flow.sign_up.disabled", exception.detailsId)
    }

    @Test
    fun `checkSignUpAllowed - Throws when invitation required but not bound`() = runTest {
        val oauth2 = oauth2AndClient(invitationId = null, signUpEnabled = false, invitationEnabled = true)

        val exception = assertThrows<BusinessException> {
            manager.checkSignUpAllowed(oauth2, recoverable = false)
        }
        assertEquals("flow.sign_up.invitation_required", exception.detailsId)
    }

    @Test
    fun `checkSignUpAllowed - Respects recoverable flag`() = runTest {
        val oauth2 = oauth2AndClient(signUpEnabled = false, invitationEnabled = false)

        val recoverableException = assertThrows<BusinessException> {
            manager.checkSignUpAllowed(oauth2, recoverable = true)
        }
        assertTrue(recoverableException.recoverable)

        val nonRecoverableException = assertThrows<BusinessException> {
            manager.checkSignUpAllowed(oauth2, recoverable = false)
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
