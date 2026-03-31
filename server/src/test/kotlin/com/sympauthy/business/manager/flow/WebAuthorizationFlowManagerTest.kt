package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.user.ConsentAwareCollectedClaimManager
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.code.ValidationCodeReason
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.NonInteractiveAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlow
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.config.model.EnabledMfaConfig
import io.mockk.*
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
import java.net.URI
import java.util.*

@ExtendWith(MockKExtension::class)
class WebAuthorizationFlowManagerTest {

    @MockK
    lateinit var authorizationFlowManager: AuthorizationFlowManager

    @MockK
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @MockK
    lateinit var consentAwareCollectedClaimManager: ConsentAwareCollectedClaimManager

    @MockK
    lateinit var claimValidationManager: WebAuthorizationFlowClaimValidationManager

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var uncheckedMfaConfig: EnabledMfaConfig

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
        val consentedScopes = listOf("openid", "profile")
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
            every { mock.consentedScopes } returns consentedScopes
            every { mock.mfaPassed } returns false
        }
        every { uncheckedMfaConfig.enabled } returns false
        coEvery { consentAwareCollectedClaimManager.findByUserIdAndReadableByUser(userId, consentedScopes) } returns emptyList()
        every { consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(any(), consentedScopes) } returns false
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.getStatusForOnGoingAuthorizeAttempt(authorizeAttempt)

        assertTrue(result.missingRequiredClaims)
    }

    @Test
    fun `getStatusForOnGoingAuthorizeAttempt - Non complete if missing validation`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("openid", "profile")
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
            every { mock.consentedScopes } returns consentedScopes
            every { mock.mfaPassed } returns false
        }
        every { uncheckedMfaConfig.enabled } returns false
        coEvery { consentAwareCollectedClaimManager.findByUserIdAndReadableByUser(userId, consentedScopes) } returns emptyList()
        every { consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(any(), consentedScopes) } returns true
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns listOf(
            ValidationCodeReason.EMAIL_CLAIM,
        )

        val result = manager.getStatusForOnGoingAuthorizeAttempt(authorizeAttempt)

        assertTrue(result.missingMediaForClaimValidation.isNotEmpty())
    }

    @Test
    fun `getStatusForOnGoingAuthorizeAttempt - Missing MFA when user has not passed MFA and MFA is enabled`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("openid", "profile")
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
            every { mock.consentedScopes } returns consentedScopes
            every { mock.mfaPassed } returns false
        }
        every { uncheckedMfaConfig.enabled } returns true
        coEvery { consentAwareCollectedClaimManager.findByUserIdAndReadableByUser(userId, consentedScopes) } returns emptyList()
        every { consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(any(), consentedScopes) } returns true
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.getStatusForOnGoingAuthorizeAttempt(authorizeAttempt)

        assertTrue(result.missingMfa)
    }

    @Test
    fun `getStatusForOnGoingAuthorizeAttempt - Not missing MFA when user has already passed MFA`() = runTest {
        val userId = UUID.randomUUID()
        val consentedScopes = listOf("openid", "profile")
        val authorizeAttempt = mockk<OnGoingAuthorizeAttempt> {
            val mock = this
            every { mock.userId } returns userId
            every { mock.consentedScopes } returns consentedScopes
            every { mock.mfaPassed } returns true
        }
        every { uncheckedMfaConfig.enabled } returns true
        coEvery { consentAwareCollectedClaimManager.findByUserIdAndReadableByUser(userId, consentedScopes) } returns emptyList()
        every { consentAwareCollectedClaimManager.areAllRequiredClaimsCollectedByUser(any(), consentedScopes) } returns true
        every { claimValidationManager.getReasonsToSendValidationCode(any()) } returns emptyList()

        val result = manager.getStatusForOnGoingAuthorizeAttempt(authorizeAttempt)

        assertFalse(result.missingMfa)
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

    // --- startAuthorizationWith tests ---

    private val defaultFlow = mockk<WebAuthorizationFlow>()

    private fun setupDefaultFlow() {
        every { manager.findById(AuthorizationFlow.DEFAULT_WEB_AUTHORIZATION_FLOW_ID) } returns defaultFlow
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
        val attemptSlot = slot<BusinessException?>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = null,
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                error = captureNullable(attemptSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = null,
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = null
        )

        assertEquals("client.parse_requested.missing", attemptSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Stores error when client_id is unknown`() = runTest {
        val clientException = businessExceptionOf(detailsId = "client.invalid_client_id")
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient("unknown") } throws clientException
        val attemptSlot = slot<BusinessException?>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = null,
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                error = captureNullable(attemptSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "unknown",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = null
        )

        assertEquals("client.invalid_client_id", attemptSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Uses default flow when client has no authorizationFlow`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns false
        }
        setupDefaultFlow()
        setupValidClient(client)
        val flowSlot = slot<WebAuthorizationFlow>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = any(),
                authorizationFlow = capture(flowSlot),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
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
        }
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery { scopeManager.parseRequestedScopes(client, any()) } throws businessExceptionOf(detailsId = "scope.unsupported")
        every { manager.parseRequestedRedirectUri(client, any()) } returns URI("https://example.com/callback")
        val attemptSlot = slot<BusinessException?>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                error = captureNullable(attemptSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "client",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = "invalid_scope",
            uncheckedRedirectUri = "https://example.com/callback"
        )

        assertEquals("scope.unsupported", attemptSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Skips scope validation when client is null`() = runTest {
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(null) } throws businessExceptionOf(detailsId = "client.parse_requested.missing")
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
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
        }
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery { scopeManager.parseRequestedScopes(client, any()) } returns emptyList()
        every { manager.parseRequestedRedirectUri(client, any()) } throws businessExceptionOf(
            detailsId = "flow.web.parse_requested_redirect_uri.missing"
        )
        val attemptSlot = slot<BusinessException?>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                error = captureNullable(attemptSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = "client",
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = null,
            uncheckedRedirectUri = ""
        )

        assertEquals("flow.web.parse_requested_redirect_uri.missing", attemptSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Skips redirect_uri validation when client is null`() = runTest {
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(null) } throws businessExceptionOf(detailsId = "client.parse_requested.missing")
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
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
        }
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery { scopeManager.parseRequestedScopes(client, any()) } returns emptyList()
        every { manager.parseRequestedRedirectUri(client, any()) } returns URI("https://example.com/callback")
        val attemptSlot = slot<BusinessException?>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                error = captureNullable(attemptSlot)
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

        assertEquals("authorize.pkce.missing_code_challenge", attemptSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Stores code_challenge and S256 on valid PKCE request`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns false
        }
        setupDefaultFlow()
        setupValidClient(client)
        val challengeSlot = slot<String?>()
        val methodSlot = slot<CodeChallengeMethod?>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = captureNullable(challengeSlot),
                codeChallengeMethod = captureNullable(methodSlot),
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
        val attemptSlot = slot<BusinessException?>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                error = captureNullable(attemptSlot)
            )
        } returns mockk()

        manager.startAuthorizationWith(
            uncheckedClientId = null,
            uncheckedClientState = null,
            uncheckedClientNonce = null,
            uncheckedScopes = "invalid",
            uncheckedRedirectUri = ""
        )

        assertEquals("client.parse_requested.missing", attemptSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Uses first error when multiple validations fail`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns true
        }
        setupDefaultFlow()
        coEvery { clientManager.parseRequestedClient(any()) } returns client
        coEvery { scopeManager.parseRequestedScopes(client, any()) } throws businessExceptionOf(detailsId = "scope.unsupported")
        every { manager.parseRequestedRedirectUri(client, any()) } throws businessExceptionOf(
            detailsId = "flow.web.parse_requested_redirect_uri.missing"
        )
        val attemptSlot = slot<BusinessException?>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = any(),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
                error = captureNullable(attemptSlot)
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
        assertEquals("scope.unsupported", attemptSlot.captured?.detailsId)
    }

    @Test
    fun `startAuthorizationWith - Passes state to newAuthorizeAttempt`() = runTest {
        val client = mockk<Client> {
            every { authorizationFlow } returns null
            every { `public` } returns false
        }
        setupDefaultFlow()
        setupValidClient(client)
        val stateSlot = slot<String?>()
        coEvery {
            authorizeAttemptManager.newAuthorizeAttempt(
                client = any(),
                clientState = captureNullable(stateSlot),
                authorizationFlow = any(),
                scopes = any(),
                redirectUri = any(),
                codeChallenge = any(),
                codeChallengeMethod = any(),
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
            every { allowedRedirectUris } returns listOf(URI("https://allowed.com/callback"))
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://other.com/callback")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Accepts redirect_uri when allowedRedirectUris is null`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns null
        }
        val result = manager.parseRequestedRedirectUri(client, "https://any.com/callback")
        assertEquals(URI("https://any.com/callback"), result)
    }

    @Test
    fun `parseRequestedRedirectUri - Accepts redirect_uri when allowedRedirectUris is empty`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns emptyList()
        }
        val result = manager.parseRequestedRedirectUri(client, "https://any.com/callback")
        assertEquals(URI("https://any.com/callback"), result)
    }

    @Test
    fun `parseRequestedRedirectUri - Accepts redirect_uri matching an allowed URI exactly`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf(URI("https://example.com/callback"))
        }
        val result = manager.parseRequestedRedirectUri(client, "https://example.com/callback")
        assertEquals(URI("https://example.com/callback"), result)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with different path`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf(URI("https://example.com/callback"))
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://example.com/other-path")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.not_allowed", exception.detailsId)
    }

    @Test
    fun `parseRequestedRedirectUri - Rejects redirect_uri with extra query params`() {
        val client = mockk<Client> {
            every { allowedRedirectUris } returns listOf(URI("https://example.com/callback"))
        }
        val exception = assertThrows<BusinessException> {
            manager.parseRequestedRedirectUri(client, "https://example.com/callback?extra=param")
        }
        assertEquals("flow.web.parse_requested_redirect_uri.not_allowed", exception.detailsId)
    }
}
