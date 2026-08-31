package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionOAuth2Manager
import com.sympauthy.business.manager.rule.ScopeGrantingRuleManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.business.model.user.CollectedClaim
import com.sympauthy.config.model.EnabledFeaturesConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class UserScopeGrantingManagerTest {

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var authorizationWebhookScopeGrantingManager: AuthorizationWebhookUserScopeGrantingManager

    @MockK
    lateinit var scopeGrantingRuleManager: ScopeGrantingRuleManager

    @MockK
    lateinit var oauth2Manager: InteractiveFlowSessionOAuth2Manager

    @MockK
    lateinit var featuresConfig: EnabledFeaturesConfig

    @SpyK
    @InjectMockKs
    lateinit var scopeGrantingManager: UserScopeGrantingManager

    @Test
    fun `grantScopes - apply methods returned by getScopeGrantingMethods`() = runBlocking {
        val session = mockk<OnGoingInteractiveFlowSession>()
        coEvery { oauth2Manager.fetchOAuth2(session) } returns InteractiveFlowSessionOAuth2(
            sessionId = UUID.randomUUID(),
            clientId = "test-client",
            redirectUri = "https://example.com/callback",
            requestedScopes = listOf("grantedScope1", "declinedScope1", "declinedScope2")
        )

        val grantedScope1 = GrantableUserScope("grantedScope1", discoverable = false)
        val declinedScope1 = GrantableUserScope("declinedScope1", discoverable = false)
        val declinedScope2 = GrantableUserScope("declinedScope2", discoverable = false)

        coEvery { scopeManager.findOrThrow("grantedScope1") } returns grantedScope1
        coEvery { scopeManager.findOrThrow("declinedScope1") } returns declinedScope1
        coEvery { scopeManager.findOrThrow("declinedScope2") } returns declinedScope2

        val method1: suspend (session: InteractiveFlowSession, requestedScopes: List<EnabledScope>, collectedClaims: List<CollectedClaim>) -> ScopeGrantingMethodResult =
            { _, _, _ ->
                ScopeGrantingMethodResult(
                    grantedScopes = listOf(grantedScope1),
                    declinedScopes = listOf(declinedScope1)
                )
            }
        val declineAllMethod: suspend (session: InteractiveFlowSession, requestedScopes: List<EnabledScope>, collectedClaims: List<CollectedClaim>) -> ScopeGrantingMethodResult =
            { _, requestedScopes, _ ->
                ScopeGrantingMethodResult(
                    grantedScopes = emptyList(),
                    declinedScopes = requestedScopes
                )
            }

        every { scopeGrantingManager.getScopeGrantingMethods() } returns listOf(method1, declineAllMethod)

        val result = scopeGrantingManager.grantScopes(
            session = session,
            allClaims = emptyList()
        )

        assertEquals(listOf(grantedScope1, declinedScope1, declinedScope2), result.requestedScopes)
        assertEquals(listOf(grantedScope1), result.grantedScopes)
        assertEquals(listOf(declinedScope1, declinedScope2), result.declinedScopes)
    }

    @Test
    fun getUnhandledRequestedScopes() {
        val grantedScope = mockk<EnabledScope>()
        val declinedScope = mockk<EnabledScope>()
        val unhandledScope = mockk<EnabledScope>()

        val result = ScopeGrantingMethodResult(
            grantedScopes = listOf(grantedScope),
            declinedScopes = listOf(declinedScope)
        )

        val scopes = scopeGrantingManager.getUnhandledRequestedScopes(
            requestedScopes = listOf(grantedScope, declinedScope, unhandledScope),
            results = listOf(result)
        )
        assertEquals(listOf(unhandledScope), scopes)
    }

    @Test
    fun `applyDefaultBehavior - decline all requested scopes when grantUnhandledScopes is disabled`() = runBlocking {
        val scope = mockk<EnabledScope>()
        every { featuresConfig.grantUnhandledScopes } returns false

        val result = scopeGrantingManager.applyDefaultBehavior(
            session = mockk(),
            requestedScopes = listOf(scope),
        )

        assertTrue(result.grantedScopes.isEmpty())
        assertEquals(listOf(scope), result.declinedScopes)
    }

    @Test
    fun `applyDefaultBehavior - grant all requested scopes when grantUnhandledScopes is enabled`() = runBlocking {
        val scope = mockk<EnabledScope>()
        every { featuresConfig.grantUnhandledScopes } returns true

        val result = scopeGrantingManager.applyDefaultBehavior(
            session = mockk(),
            requestedScopes = listOf(scope),
        )

        assertEquals(listOf(scope), result.grantedScopes)
        assertTrue(result.declinedScopes.isEmpty())
    }

    @Test
    fun `UserGrantScopesResult - grantedScopes - List all scopes granted in result of different methods`() {
        val grantedScope1 = mockk<EnabledScope>()
        val grantedScope2 = mockk<EnabledScope>()
        val grantedScope3 = mockk<EnabledScope>()

        val result1 = ScopeGrantingMethodResult(
            grantedScopes = listOf(grantedScope1),
        )
        val result2 = ScopeGrantingMethodResult(
            grantedScopes = listOf(grantedScope2, grantedScope3),
        )
        val result3 = ScopeGrantingMethodResult()

        val result = UserGrantScopesResult(
            requestedScopes = emptyList(),
            results = listOf(result1, result2, result3)
        )
        assertEquals(listOf(grantedScope1, grantedScope2, grantedScope3), result.grantedScopes)
    }

    @Test
    fun `UserGrantScopesResult - declineScope - List all scopes declined in result of different methods`() {
        val declinedScope1 = mockk<EnabledScope>()
        val declinedScope2 = mockk<EnabledScope>()
        val declinedScope3 = mockk<EnabledScope>()

        val result1 = ScopeGrantingMethodResult(
            declinedScopes = listOf(declinedScope1),
        )
        val result2 = ScopeGrantingMethodResult(
            declinedScopes = listOf(declinedScope2, declinedScope3),
        )
        val result3 = ScopeGrantingMethodResult()

        val result = UserGrantScopesResult(
            requestedScopes = emptyList(),
            results = listOf(result1, result2, result3)
        )
        assertEquals(listOf(declinedScope1, declinedScope2, declinedScope3), result.declinedScopes)
    }
}
