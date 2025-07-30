package com.sympauthy.business.manager.auth

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.rule.ScopeGrantingRuleManager
import com.sympauthy.business.model.ScopeGrantingMethodResult
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.Scope
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
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class AuthorizationManagerTest {

    @MockK
    lateinit var scopeManager: ScopeManager

    @MockK
    lateinit var scopeGrantingRuleManager: ScopeGrantingRuleManager

    @SpyK
    @InjectMockKs
    lateinit var authorizationManager: AuthorizationManager

    @Test
    fun `grantScopes - apply methods returned by getScopeGrantingMethods`() = runBlocking {
        val authorizeAttempt = mockk<AuthorizeAttempt>()
        every { authorizeAttempt.requestedScopes } returns listOf("grantedScope1", "declinedScope1", "declinedScope2")

        val grantedScope1 = mockk<Scope>()
        val declinedScope1 = mockk<Scope>()
        val declinedScope2 = mockk<Scope>()

        coEvery { scopeManager.findOrThrow("grantedScope1") } returns grantedScope1
        coEvery { scopeManager.findOrThrow("declinedScope1") } returns declinedScope1
        coEvery { scopeManager.findOrThrow("declinedScope2") } returns declinedScope2

        val method1: suspend (authorizeAttempt: AuthorizeAttempt, requestedScopes: List<Scope>) -> ScopeGrantingMethodResult =
            { _, _ ->
                ScopeGrantingMethodResult(
                    grantedScopes = listOf(grantedScope1),
                    declinedScopes = listOf(declinedScope1)
                )
            }
        val declineAllMethod: suspend (authorizeAttempt: AuthorizeAttempt, requestedScopes: List<Scope>) -> ScopeGrantingMethodResult =
            { _, requestedScopes ->
                ScopeGrantingMethodResult(
                    grantedScopes = emptyList(),
                    declinedScopes = requestedScopes
                )
            }

        every { authorizationManager.getScopeGrantingMethods() } returns listOf(method1, declineAllMethod)

        val result = authorizationManager.grantScopes(authorizeAttempt)

        assertEquals(listOf(grantedScope1, declinedScope1, declinedScope2), result.requestedScopes)
        assertEquals(listOf(grantedScope1), result.grantedScopes)
        assertEquals(listOf(declinedScope1, declinedScope2), result.declinedScopes)
    }

    @Test
    fun getUnhandledRequestedScopes() {
        val grantedScope = mockk<Scope>()
        val declinedScope = mockk<Scope>()
        val unhandledScope = mockk<Scope>()

        val result = ScopeGrantingMethodResult(
            grantedScopes = listOf(grantedScope),
            declinedScopes = listOf(declinedScope)
        )

        val scopes = authorizationManager.getUnhandledRequestedScopes(
            requestedScopes = listOf(grantedScope, declinedScope, unhandledScope),
            results = listOf(result)
        )
        assertEquals(listOf(unhandledScope), scopes)
    }

    @Test
    fun `applyDefaultBehavior - decline all requested scopes`() = runBlocking {
        val scope = mockk<Scope>()

        val result = authorizationManager.applyDefaultBehavior(
            authorizeAttempt = mockk(),
            requestedScopes = listOf(scope),
        )

        assertTrue(result.grantedScopes.isEmpty())
        assertEquals(listOf(scope), result.declinedScopes)
    }

    @Test
    fun `GrantScopesResult - grantedScopes - List all scopes granted in result of different methods`() {
        val grantedScope1 = mockk<Scope>()
        val grantedScope2 = mockk<Scope>()
        val grantedScope3 = mockk<Scope>()

        val result1 = ScopeGrantingMethodResult(
            grantedScopes = listOf(grantedScope1),
        )
        val result2 = ScopeGrantingMethodResult(
            grantedScopes = listOf(grantedScope2, grantedScope3),
        )
        val result3 = ScopeGrantingMethodResult()

        val result = GrantScopesResult(
            requestedScopes = emptyList(),
            results = listOf(result1, result2, result3)
        )
        assertEquals(listOf(grantedScope1, grantedScope2, grantedScope3), result.grantedScopes)
    }

    @Test
    fun `GrantScopesResult - declineScope - List all scopes declined in result of different methods`() {
        val declinedScope1 = mockk<Scope>()
        val declinedScope2 = mockk<Scope>()
        val declinedScope3 = mockk<Scope>()

        val result1 = ScopeGrantingMethodResult(
            declinedScopes = listOf(declinedScope1),
        )
        val result2 = ScopeGrantingMethodResult(
            declinedScopes = listOf(declinedScope2, declinedScope3),
        )
        val result3 = ScopeGrantingMethodResult()

        val result = GrantScopesResult(
            requestedScopes = emptyList(),
            results = listOf(result1, result2, result3)
        )
        assertEquals(listOf(declinedScope1, declinedScope2, declinedScope3), result.declinedScopes)
    }
}
