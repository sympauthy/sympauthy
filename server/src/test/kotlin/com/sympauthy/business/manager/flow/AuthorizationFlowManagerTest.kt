package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.UserGrantScopesResult
import com.sympauthy.business.manager.auth.UserScopeGrantingManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.CompletedInteractiveFlowSession
import com.sympauthy.business.model.flow.FailedInteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.EnabledFeaturesConfig
import com.sympauthy.config.model.UrlsConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
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
class AuthorizationFlowManagerTest {

    @MockK
    lateinit var sessionManager: InteractiveFlowSessionManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var collectedClaimManager: CollectedClaimManager

    @MockK
    lateinit var scopeGrantingManager: UserScopeGrantingManager

    @MockK
    lateinit var consentManager: ConsentManager

    @MockK
    lateinit var authorizationFlowsConfig: AuthorizationFlowsConfig

    @MockK
    lateinit var uncheckedUrlsConfig: UrlsConfig

    @MockK
    lateinit var uncheckedFeaturesConfig: EnabledFeaturesConfig

    @MockK
    lateinit var clientManager: ClientManager

    private val testAudience = Audience(id = "test-audience", tokenAudience = "test-audience")
    private val clientManagerProvider: Provider<ClientManager> = Provider { clientManager }

    @InjectMockKs
    lateinit var manager: AuthorizationFlowManager

    // --- checkCanIssueToken tests ---

    @Test
    fun `checkCanIssueToken - Throws when session is null`() = runTest {
        val client = mockClient()

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(null, null, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when session is ongoing`() = runTest {
        val client = mockClient()
        val onGoingSession = createOnGoingSession(userId = UUID.randomUUID())

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(onGoingSession, null, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when session has failed`() = runTest {
        val client = mockClient()
        val failedSession = FailedInteractiveFlowSession(
            id = UUID.randomUUID(),
            purpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            errorDetailsId = "some.error",
            errorDate = LocalDateTime.now()
        )

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(failedSession, null, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when session is expired`() = runTest {
        val client = mockClient("test-client")
        val completedSession = createCompletedSession(
            expirationDate = LocalDateTime.now().minusMinutes(1)
        )
        val oauth2 = oauth2Of(sessionId = completedSession.id, clientId = "test-client")

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(completedSession, oauth2, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when oauth2 record is missing`() = runTest {
        val client = mockClient("test-client")
        val completedSession = createCompletedSession()

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(completedSession, null, client)
        }
        assertEquals("token.expired", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Throws when client does not match`() = runTest {
        val client = mockClient("other-client")
        val completedSession = createCompletedSession()
        val oauth2 = oauth2Of(sessionId = completedSession.id, clientId = "test-client")

        val exception = assertThrows<BusinessException> {
            manager.checkCanIssueToken(completedSession, oauth2, client)
        }
        assertEquals("token.mismatching_client", exception.detailsId)
    }

    @Test
    fun `checkCanIssueToken - Returns completed session and oauth2 when valid`() = runTest {
        val client = mockClient("test-client")
        val completedSession = createCompletedSession()
        val oauth2 = oauth2Of(sessionId = completedSession.id, clientId = "test-client")

        val (resultSession, resultOAuth2) = manager.checkCanIssueToken(completedSession, oauth2, client)

        assertSame(completedSession, resultSession)
        assertSame(oauth2, resultOAuth2)
    }

    // --- completeAuthorization tests ---

    @Test
    fun `completeAuthorization - Returns session unchanged when already completed`() = runTest {
        val completedSession = mockk<CompletedInteractiveFlowSession>()

        val result = manager.completeAuthorization(completedSession)

        assertSame(completedSession, result)
    }

    @Test
    fun `completeAuthorization - Returns session unchanged when already failed`() = runTest {
        val failedSession = mockk<FailedInteractiveFlowSession>()

        val result = manager.completeAuthorization(failedSession)

        assertSame(failedSession, result)
    }

    @Test
    fun `completeAuthorization - Marks as complete when scopes are granted`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val grantedScopes = listOf("read")
        val grantedScopeObjects = grantedScopes.map { mockkScope(it) }
        val onGoingSession = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(
            sessionId = onGoingSession.id,
            clientId = clientId,
            grantedScopes = grantedScopes,
            consentedScopes = emptyList()
        )
        val completedSession = mockk<CompletedInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
        }
        val collectedClaims = emptyList<CollectedClaim>()

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns false
        coEvery { collectedClaimManager.findByUserId(userId) } returns collectedClaims

        val grantScopesResult = UserGrantScopesResult(
            requestedScopes = grantedScopeObjects,
            results = listOf(
                ScopeGrantingMethodResult(
                    grantedScopes = grantedScopeObjects,
                    declinedScopes = emptyList()
                )
            )
        )
        coEvery { scopeGrantingManager.grantScopes(onGoingSession, collectedClaims) } returns grantScopesResult
        coEvery {
            oauth2Manager.setGrantedScopes(onGoingSession, grantedScopeObjects, any())
        } returns oauth2AfterGranted
        coEvery { sessionManager.markAsComplete(onGoingSession) } returns completedSession

        coEvery { clientManager.findClientById(clientId) } returns mockClient(clientId)
        coEvery { consentManager.saveConsent(userId, any(), clientId, emptyList()) } returns mockk()

        val result = manager.completeAuthorization(onGoingSession)

        assertSame(completedSession, result)
        coVerify(exactly = 1) { consentManager.saveConsent(userId, any(), clientId, emptyList()) }
    }

    @Test
    fun `completeAuthorization - Marks as complete when no scopes granted but allowAccessToClientWithoutScope is true`() =
        runTest {
            val userId = UUID.randomUUID()
            val clientId = "client-id"
            val onGoingSession = createOnGoingSession(userId = userId)
            val oauth2AfterGranted = oauth2Of(
                sessionId = onGoingSession.id,
                clientId = clientId,
                grantedScopes = emptyList(),
                consentedScopes = emptyList()
            )
            val completedSession = mockk<CompletedInteractiveFlowSession> {
                every { this@mockk.userId } returns userId
            }
            val collectedClaims = emptyList<CollectedClaim>()

            every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns true
            coEvery { collectedClaimManager.findByUserId(userId) } returns collectedClaims

            val grantScopesResult = UserGrantScopesResult(
                requestedScopes = emptyList(),
                results = listOf(
                    ScopeGrantingMethodResult(
                        grantedScopes = emptyList(),
                        declinedScopes = emptyList()
                    )
                )
            )
            coEvery { scopeGrantingManager.grantScopes(onGoingSession, collectedClaims) } returns grantScopesResult
            coEvery {
                oauth2Manager.setGrantedScopes(onGoingSession, emptyList(), any())
            } returns oauth2AfterGranted
            coEvery { sessionManager.markAsComplete(onGoingSession) } returns completedSession

            coEvery { clientManager.findClientById(clientId) } returns mockClient(clientId)
            coEvery { consentManager.saveConsent(userId, any(), clientId, emptyList()) } returns mockk()

            val result = manager.completeAuthorization(onGoingSession)

            assertSame(completedSession, result)
            coVerify(exactly = 1) { consentManager.saveConsent(userId, any(), clientId, emptyList()) }
        }

    @Test
    fun `completeAuthorization - Marks as failed when no scopes granted and allowAccessToClientWithoutScope is false`() =
        runTest {
            val userId = UUID.randomUUID()
            val onGoingSession = createOnGoingSession(userId = userId)
            val oauth2AfterGranted = oauth2Of(
                sessionId = onGoingSession.id,
                clientId = "client-id",
                grantedScopes = emptyList(),
                consentedScopes = emptyList()
            )
            val failedSession = mockk<FailedInteractiveFlowSession>()
            val collectedClaims = emptyList<CollectedClaim>()

            every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns false
            coEvery { collectedClaimManager.findByUserId(userId) } returns collectedClaims

            val grantScopesResult = UserGrantScopesResult(
                requestedScopes = emptyList(),
                results = listOf(
                    ScopeGrantingMethodResult(
                        grantedScopes = emptyList(),
                        declinedScopes = emptyList()
                    )
                )
            )
            coEvery { scopeGrantingManager.grantScopes(onGoingSession, collectedClaims) } returns grantScopesResult
            coEvery {
                oauth2Manager.setGrantedScopes(onGoingSession, emptyList(), any())
            } returns oauth2AfterGranted
            coEvery {
                sessionManager.markAsFailedIfNotRecoverable(onGoingSession, any())
            } returns failedSession

            val result = manager.completeAuthorization(onGoingSession)

            assertEquals(failedSession, result)
            coVerify {
                sessionManager.markAsFailedIfNotRecoverable(
                    session = onGoingSession,
                    error = match<BusinessException> { it.detailsId == "flow.authorization_flow.complete.no_scope" }
                )
            }
        }

    @Test
    fun `completeAuthorization - Fetches all claims and passes them to grantScopes`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val grantedScopes = listOf("read")
        val grantedScopeObjects = grantedScopes.map { mockkScope(it) }
        val onGoingSession = createOnGoingSession(userId = userId)
        val oauth2AfterGranted = oauth2Of(
            sessionId = onGoingSession.id,
            clientId = clientId,
            grantedScopes = grantedScopes,
            consentedScopes = emptyList()
        )
        val completedSession = mockk<CompletedInteractiveFlowSession> {
            every { this@mockk.userId } returns userId
        }
        val collectedClaims = listOf(mockk<CollectedClaim>())

        coEvery { collectedClaimManager.findByUserId(userId) } returns collectedClaims

        val grantScopesResult = UserGrantScopesResult(
            requestedScopes = emptyList(),
            results = listOf(
                ScopeGrantingMethodResult(
                    grantedScopes = grantedScopeObjects,
                    declinedScopes = emptyList()
                )
            )
        )
        coEvery { scopeGrantingManager.grantScopes(onGoingSession, collectedClaims) } returns grantScopesResult
        coEvery {
            oauth2Manager.setGrantedScopes(onGoingSession, grantedScopeObjects, any())
        } returns oauth2AfterGranted
        coEvery { sessionManager.markAsComplete(onGoingSession) } returns completedSession

        coEvery { clientManager.findClientById(clientId) } returns mockClient(clientId)
        coEvery { consentManager.saveConsent(userId, any(), clientId, emptyList()) } returns mockk()

        val result = manager.completeAuthorization(onGoingSession)

        assertSame(completedSession, result)
        coVerify { collectedClaimManager.findByUserId(userId) }
    }

    private fun mockClient(id: String = "test-client"): Client {
        return mockk {
            every { this@mockk.id } returns id
            every { audience } returns testAudience
        }
    }

    private fun createOnGoingSession(
        userId: UUID?
    ): OnGoingInteractiveFlowSession {
        return OnGoingInteractiveFlowSession(
            id = UUID.randomUUID(),
            purpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            sessionDate = LocalDateTime.now(),
            userId = userId
        )
    }

    private fun createCompletedSession(
        expirationDate: LocalDateTime = LocalDateTime.now().plusHours(1)
    ): CompletedInteractiveFlowSession {
        val now = LocalDateTime.now()
        return CompletedInteractiveFlowSession(
            id = UUID.randomUUID(),
            purpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            flowId = "flow-id",
            expirationDate = expirationDate,
            sessionDate = now,
            userId = UUID.randomUUID(),
            completeDate = now
        )
    }

    private fun oauth2Of(
        sessionId: UUID,
        clientId: String = "client-id",
        consentedScopes: List<String>? = null,
        grantedScopes: List<String>? = null
    ): InteractiveFlowSessionOAuth2 {
        return InteractiveFlowSessionOAuth2(
            sessionId = sessionId,
            clientId = clientId,
            redirectUri = "https://example.com/callback",
            requestedScopes = emptyList(),
            state = "state",
            nonce = "nonce",
            consentedScopes = consentedScopes,
            consentedAt = consentedScopes?.let { LocalDateTime.now() },
            consentedBy = consentedScopes?.let { ConsentedBy.AUTO },
            grantedScopes = grantedScopes
        )
    }

    private fun mockkScope(scope: String): Scope {
        return mockk<Scope> {
            every { this@mockk.scope } returns scope
        }
    }
}
