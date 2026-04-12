package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.client.authorization.webhook.AuthorizationWebhookClient
import com.sympauthy.client.authorization.webhook.model.AuthorizationWebhookRequest
import com.sympauthy.client.authorization.webhook.model.AuthorizationWebhookResponse
import com.sympauthy.client.authorization.webhook.model.AuthorizationWebhookResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class AuthorizationWebhookUserScopeGrantingManagerTest {

    @MockK
    lateinit var clientManager: ClientManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var authorizationWebhookClient: AuthorizationWebhookClient

    lateinit var manager: AuthorizationWebhookUserScopeGrantingManager

    @BeforeEach
    fun setUp() {
        manager = AuthorizationWebhookUserScopeGrantingManager(
            clientManagerProvider = { clientManager },
            scopeManager = scopeManager,
            authorizationWebhookClient = authorizationWebhookClient
        )
    }

    private val scope1 = GrantableUserScope("scope1", discoverable = false)
    private val scope2 = GrantableUserScope("scope2", discoverable = false)

    private fun mockAuthorizeAttempt(
        clientId: String = "test-client",
        userId: UUID = UUID.randomUUID()
    ): OnGoingAuthorizeAttempt {
        val attempt = mockk<OnGoingAuthorizeAttempt>()
        every { attempt.clientId } returns clientId
        every { attempt.userId } returns userId
        return attempt
    }

    private fun mockClient(
        id: String = "test-client",
        authorizationWebhook: AuthorizationWebhook? = null,
        allowedScopes: Set<Scope>? = null
    ): Client {
        return Client(
            id = id,
            secret = "secret",
            allowedGrantTypes = setOf(GrantType.AUTHORIZATION_CODE),
            authorizationFlow = null,
            authorizationWebhook = authorizationWebhook,
            allowedScopes = allowedScopes
        )
    }

    private fun mockWebhookConfig(
        onFailure: AuthorizationWebhookOnFailure = AuthorizationWebhookOnFailure.DENY_ALL
    ): AuthorizationWebhook {
        return AuthorizationWebhook(
            url = URI.create("https://example.com/webhook"),
            secret = "test-secret",
            onFailure = onFailure,
        )
    }

    @Test
    fun `applyAuthorizationWebhookScopeGranting - returns empty result when no webhook configured`() = runTest {
        val attempt = mockAuthorizeAttempt()
        val client = mockClient()
        coEvery { clientManager.findClientById("test-client") } returns client

        val result = manager.applyAuthorizationWebhookScopeGranting(
            attempt, listOf(scope1, scope2), emptyList()
        )

        assertTrue(result.grantedScopes.isEmpty())
        assertTrue(result.declinedScopes.isEmpty())
    }

    @Test
    fun `applyAuthorizationWebhookScopeGranting - grants and denies scopes based on webhook response`() = runTest {
        val attempt = mockAuthorizeAttempt()
        val client = mockClient(authorizationWebhook = mockWebhookConfig())
        coEvery { clientManager.findClientById("test-client") } returns client
        coEvery {
            authorizationWebhookClient.callWebhook(any(), any<AuthorizationWebhookRequest>())
        } returns AuthorizationWebhookResult.Success(
            AuthorizationWebhookResponse(
                scopes = mapOf("scope1" to "grant", "scope2" to "deny")
            )
        )

        val result = manager.applyAuthorizationWebhookScopeGranting(
            attempt, listOf(scope1, scope2), emptyList()
        )

        assertEquals(listOf(scope1), result.grantedScopes)
        assertEquals(listOf(scope2), result.declinedScopes)
    }

    @Test
    fun `applyAuthorizationWebhookScopeGranting - unspecified scopes in response default to deny`() = runTest {
        val attempt = mockAuthorizeAttempt()
        val client = mockClient(authorizationWebhook = mockWebhookConfig())
        coEvery { clientManager.findClientById("test-client") } returns client
        coEvery {
            authorizationWebhookClient.callWebhook(any(), any<AuthorizationWebhookRequest>())
        } returns AuthorizationWebhookResult.Success(
            AuthorizationWebhookResponse(
                scopes = mapOf("scope1" to "grant")
            )
        )

        val result = manager.applyAuthorizationWebhookScopeGranting(
            attempt, listOf(scope1, scope2), emptyList()
        )

        assertEquals(listOf(scope1), result.grantedScopes)
        assertEquals(listOf(scope2), result.declinedScopes)
    }

    @Test
    fun `applyAuthorizationWebhookScopeGranting - declines all scopes on failure when onFailure is DENY_ALL`() =
        runTest {
            val attempt = mockAuthorizeAttempt()
            val client = mockClient(
                authorizationWebhook = mockWebhookConfig(onFailure = AuthorizationWebhookOnFailure.DENY_ALL)
            )
            coEvery { clientManager.findClientById("test-client") } returns client
            coEvery {
                authorizationWebhookClient.callWebhook(any(), any<AuthorizationWebhookRequest>())
            } returns AuthorizationWebhookResult.Failure(message = "Connection refused")

            val result = manager.applyAuthorizationWebhookScopeGranting(
                attempt, listOf(scope1, scope2), emptyList()
            )

            assertTrue(result.grantedScopes.isEmpty())
            assertEquals(listOf(scope1, scope2), result.declinedScopes)
        }

    @Test
    fun `applyAuthorizationWebhookScopeGranting - returns empty result on failure when onFailure is FALLBACK_TO_RULES`() =
        runTest {
            val attempt = mockAuthorizeAttempt()
            val client = mockClient(
                authorizationWebhook = mockWebhookConfig(onFailure = AuthorizationWebhookOnFailure.FALLBACK_TO_RULES)
            )
            coEvery { clientManager.findClientById("test-client") } returns client
            coEvery {
                authorizationWebhookClient.callWebhook(any(), any<AuthorizationWebhookRequest>())
            } returns AuthorizationWebhookResult.Failure(message = "Connection refused")

            val result = manager.applyAuthorizationWebhookScopeGranting(
                attempt, listOf(scope1, scope2), emptyList()
            )

            assertTrue(result.grantedScopes.isEmpty())
            assertTrue(result.declinedScopes.isEmpty())
        }

    @Test
    fun `applyAuthorizationWebhookScopeGranting - grants additional scopes within allowed-scopes`() = runTest {
        val extraScope = GrantableUserScope("extra-scope", discoverable = false)
        val attempt = mockAuthorizeAttempt()
        val client = mockClient(
            authorizationWebhook = mockWebhookConfig(),
            allowedScopes = setOf(scope1, scope2, extraScope)
        )
        coEvery { clientManager.findClientById("test-client") } returns client
        coEvery { scopeManager.find("extra-scope") } returns extraScope
        coEvery {
            authorizationWebhookClient.callWebhook(any(), any<AuthorizationWebhookRequest>())
        } returns AuthorizationWebhookResult.Success(
            AuthorizationWebhookResponse(
                scopes = mapOf("scope1" to "grant", "extra-scope" to "grant")
            )
        )

        val result = manager.applyAuthorizationWebhookScopeGranting(
            attempt, listOf(scope1), emptyList()
        )

        assertEquals(listOf(scope1, extraScope), result.grantedScopes)
    }

    @Test
    fun `applyAuthorizationWebhookScopeGranting - does not grant scopes outside allowed-scopes`() = runTest {
        val extraScope = GrantableUserScope("extra-scope", discoverable = false)
        val attempt = mockAuthorizeAttempt()
        val client = mockClient(
            authorizationWebhook = mockWebhookConfig(),
            allowedScopes = setOf(scope1)
        )
        coEvery { clientManager.findClientById("test-client") } returns client
        coEvery { scopeManager.find("extra-scope") } returns extraScope
        coEvery {
            authorizationWebhookClient.callWebhook(any(), any<AuthorizationWebhookRequest>())
        } returns AuthorizationWebhookResult.Success(
            AuthorizationWebhookResponse(
                scopes = mapOf("scope1" to "grant", "extra-scope" to "grant")
            )
        )

        val result = manager.applyAuthorizationWebhookScopeGranting(
            attempt, listOf(scope1), emptyList()
        )

        assertEquals(listOf(scope1), result.grantedScopes)
    }
}
