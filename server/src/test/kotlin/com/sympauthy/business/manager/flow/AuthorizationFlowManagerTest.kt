package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.auth.GrantScopesResult
import com.sympauthy.business.manager.auth.ScopeGrantingManager
import com.sympauthy.business.manager.consent.ConsentManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.oauth2.CompletedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.FailedAuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.oauth2.Scope
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class AuthorizationFlowManagerTest {

    @MockK
    lateinit var authorizeAttemptManager: AuthorizeAttemptManager

    @MockK
    lateinit var scopeGrantingManager: ScopeGrantingManager

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var consentManager: ConsentManager

    @MockK
    lateinit var authorizationFlowsConfig: AuthorizationFlowsConfig

    @MockK
    lateinit var uncheckedUrlsConfig: UrlsConfig

    @MockK
    lateinit var uncheckedFeaturesConfig: EnabledFeaturesConfig

    @InjectMockKs
    lateinit var manager: AuthorizationFlowManager

    @Test
    fun `completeAuthorization - Returns attempt unchanged when already completed`() = runTest {
        val completedAttempt = mockk<CompletedAuthorizeAttempt>()
        val collectedClaims = emptyList<CollectedClaim>()

        val result = manager.completeAuthorization(completedAttempt, collectedClaims)

        assertEquals(completedAttempt, result)
    }

    @Test
    fun `completeAuthorization - Returns attempt unchanged when already failed`() = runTest {
        val failedAttempt = mockk<FailedAuthorizeAttempt>()
        val collectedClaims = emptyList<CollectedClaim>()

        val result = manager.completeAuthorization(failedAttempt, collectedClaims)

        assertSame(failedAttempt, result)
    }

    @Test
    fun `completeAuthorization - Marks as complete when scopes are granted`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val grantedScopes = listOf("read")
        val consentedScopes = emptyList<String>()
        val grantedScopeObjects = grantedScopes.map { mockkScope(it) }
        val onGoingAttempt = createOnGoingAuthorizeAttempt(userId = userId)
        val afterGranted = createOnGoingAuthorizeAttempt(userId = userId, grantedScopes = grantedScopes)
        val afterConsented = createOnGoingAuthorizeAttempt(userId = userId, grantedScopes = grantedScopes, consentedScopes = consentedScopes)
        val completedAttempt = mockk<CompletedAuthorizeAttempt> {
            every { this@mockk.userId } returns userId
            every { this@mockk.clientId } returns clientId
            every { this@mockk.consentedScopes } returns consentedScopes
        }
        val collectedClaims = emptyList<CollectedClaim>()

        every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns false
        coEvery { scopeManager.find(any()) } returns null

        val grantScopesResult = GrantScopesResult(
            requestedScopes = grantedScopeObjects,
            results = listOf(
                ScopeGrantingMethodResult(
                    grantedScopes = grantedScopeObjects,
                    declinedScopes = emptyList()
                )
            )
        )
        coEvery { scopeGrantingManager.grantScopes(onGoingAttempt, collectedClaims) } returns grantScopesResult
        coEvery {
            authorizeAttemptManager.setGrantedScopes(onGoingAttempt, grantedScopeObjects)
        } returns afterGranted
        coEvery {
            authorizeAttemptManager.setConsentedScopes(afterGranted, emptyList())
        } returns afterConsented
        coEvery { authorizeAttemptManager.markAsComplete(afterConsented) } returns completedAttempt
        coEvery { consentManager.saveGrantedConsent(userId, clientId, consentedScopes) } returns mockk()

        val result = manager.completeAuthorization(onGoingAttempt, collectedClaims)

        assertSame(completedAttempt, result)
        coVerify(exactly = 1) { consentManager.saveGrantedConsent(userId, clientId, consentedScopes) }
    }

    @Test
    fun `completeAuthorization - Marks as complete when no scopes granted but allowAccessToClientWithoutScope is true`() =
        runTest {
            val userId = UUID.randomUUID()
            val clientId = "client-id"
            val onGoingAttempt = createOnGoingAuthorizeAttempt(userId = userId)
            val afterGranted = createOnGoingAuthorizeAttempt(userId = userId, grantedScopes = emptyList())
            val afterConsented = createOnGoingAuthorizeAttempt(userId = userId, grantedScopes = emptyList(), consentedScopes = emptyList())
            val completedAttempt = mockk<CompletedAuthorizeAttempt> {
                every { this@mockk.userId } returns userId
                every { this@mockk.clientId } returns clientId
                every { consentedScopes } returns emptyList()
            }
            val collectedClaims = emptyList<CollectedClaim>()

            every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns true
            coEvery { scopeManager.find(any()) } returns null

            val grantScopesResult = GrantScopesResult(
                requestedScopes = emptyList(),
                results = listOf(
                    ScopeGrantingMethodResult(
                        grantedScopes = emptyList(),
                        declinedScopes = emptyList()
                    )
                )
            )
            coEvery { scopeGrantingManager.grantScopes(onGoingAttempt, collectedClaims) } returns grantScopesResult
            coEvery {
                authorizeAttemptManager.setGrantedScopes(onGoingAttempt, emptyList())
            } returns afterGranted
            coEvery {
                authorizeAttemptManager.setConsentedScopes(afterGranted, emptyList())
            } returns afterConsented
            coEvery { authorizeAttemptManager.markAsComplete(afterConsented) } returns completedAttempt
            coEvery { consentManager.saveGrantedConsent(userId, clientId, emptyList()) } returns mockk()

            val result = manager.completeAuthorization(onGoingAttempt, collectedClaims)

            assertSame(completedAttempt, result)
            coVerify(exactly = 1) { consentManager.saveGrantedConsent(userId, clientId, emptyList()) }
        }

    @Test
    fun `completeAuthorization - Marks as failed when no scopes granted and allowAccessToClientWithoutScope is false`() =
        runTest {
            val userId = UUID.randomUUID()
            val onGoingAttempt = createOnGoingAuthorizeAttempt(userId = userId)
            val afterGranted = createOnGoingAuthorizeAttempt(userId = userId, grantedScopes = emptyList())
            val afterConsented = createOnGoingAuthorizeAttempt(userId = userId, grantedScopes = emptyList(), consentedScopes = emptyList())
            val failedAttempt = mockk<FailedAuthorizeAttempt>()
            val collectedClaims = emptyList<CollectedClaim>()

            every { uncheckedFeaturesConfig.allowAccessToClientWithoutScope } returns false
            coEvery { scopeManager.find(any()) } returns null

            val grantScopesResult = GrantScopesResult(
                requestedScopes = emptyList(),
                results = listOf(
                    ScopeGrantingMethodResult(
                        grantedScopes = emptyList(),
                        declinedScopes = emptyList()
                    )
                )
            )
            coEvery { scopeGrantingManager.grantScopes(onGoingAttempt, collectedClaims) } returns grantScopesResult
            coEvery {
                authorizeAttemptManager.setGrantedScopes(onGoingAttempt, emptyList())
            } returns afterGranted
            coEvery {
                authorizeAttemptManager.setConsentedScopes(afterGranted, emptyList())
            } returns afterConsented
            coEvery {
                authorizeAttemptManager.markAsFailedIfNotRecoverable(onGoingAttempt, any())
            } returns failedAttempt

            val result = manager.completeAuthorization(onGoingAttempt, collectedClaims)

            assertEquals(failedAttempt, result)
            coVerify {
                authorizeAttemptManager.markAsFailedIfNotRecoverable(
                    authorizeAttempt = onGoingAttempt,
                    error = match<BusinessException> { it.detailsId == "flow.authorization_flow.complete.no_scope" }
                )
            }
        }

    @Test
    fun `completeAuthorization - Passes collectedClaims to grantScopes`() = runTest {
        val userId = UUID.randomUUID()
        val clientId = "client-id"
        val grantedScopes = listOf("read")
        val grantedScopeObjects = grantedScopes.map { mockkScope(it) }
        val onGoingAttempt = createOnGoingAuthorizeAttempt(userId = userId)
        val afterGranted = createOnGoingAuthorizeAttempt(userId = userId, grantedScopes = listOf("read"))
        val afterConsented = createOnGoingAuthorizeAttempt(userId = userId, grantedScopes = listOf("read"), consentedScopes = emptyList())
        val completedAttempt = mockk<CompletedAuthorizeAttempt> {
            every { this@mockk.userId } returns userId
            every { this@mockk.clientId } returns clientId
            every { this@mockk.consentedScopes } returns emptyList()
        }
        val collectedClaims = listOf(mockk<CollectedClaim>())

        coEvery { scopeManager.find(any()) } returns null

        val grantScopesResult = GrantScopesResult(
            requestedScopes = emptyList(),
            results = listOf(
                ScopeGrantingMethodResult(
                    grantedScopes = grantedScopeObjects,
                    declinedScopes = emptyList()
                )
            )
        )
        coEvery { scopeGrantingManager.grantScopes(onGoingAttempt, collectedClaims) } returns grantScopesResult
        coEvery {
            authorizeAttemptManager.setGrantedScopes(onGoingAttempt, grantedScopeObjects)
        } returns afterGranted
        coEvery {
            authorizeAttemptManager.setConsentedScopes(afterGranted, emptyList())
        } returns afterConsented
        coEvery { authorizeAttemptManager.markAsComplete(afterConsented) } returns completedAttempt
        coEvery { consentManager.saveGrantedConsent(userId, clientId, emptyList()) } returns mockk()

        val result = manager.completeAuthorization(onGoingAttempt, collectedClaims)

        assertSame(completedAttempt, result)
    }

    private fun createOnGoingAuthorizeAttempt(
        userId: UUID,
        grantedScopes: List<String>? = null,
        consentedScopes: List<String>? = null
    ): OnGoingAuthorizeAttempt {
        return OnGoingAuthorizeAttempt(
            id = UUID.randomUUID(),
            authorizationFlowId = "flow-id",
            expirationDate = LocalDateTime.now().plusHours(1),
            clientId = "client-id",
            requestedScopes = emptyList(),
            redirectUri = "https://example.com/callback",
            state = "state",
            nonce = "nonce",
            userId = userId,
            grantedScopes = grantedScopes,
            consentedScopes = consentedScopes,
            attemptDate = LocalDateTime.now()
        )
    }

    private fun mockkScope(scope: String): Scope {
        return mockk<Scope> {
            every { this@mockk.scope } returns scope
        }
    }
}
