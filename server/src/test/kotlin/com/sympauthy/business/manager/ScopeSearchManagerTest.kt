package com.sympauthy.business.manager

import com.sympauthy.business.model.filter.ValueFilter
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.DisabledScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.user.claim.Claim
import io.mockk.coEvery
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScopeSearchManagerTest {

    @MockK
    lateinit var scopeManager: ScopeManager

    @InjectMockKs
    lateinit var scopeSearchManager: ScopeSearchManager

    private val enabledScope = ConsentableUserScope(scope = "profile")
    private val disabledScope = DisabledScope(scope = "email", type = ScopeType.CONSENTABLE)
    private val grantableScope = GrantableUserScope(scope = "openid", discoverable = true)

    private val firstPage = PageParams(page = 0, size = 20)

    @Test
    fun `listScopes - Keep every scope when the criteria name nothing`() = runTest {
        knownScopes(enabledScope, disabledScope, grantableScope)

        val result = scopeSearchManager.listScopes(ValueFilter.Unfiltered, null, firstPage)

        assertEquals(listOf(disabledScope, grantableScope, enabledScope), result.items.map { it.scope })
    }

    @Test
    fun `listScopes - Keep the scopes of the type the criterion names`() = runTest {
        knownScopes(enabledScope, grantableScope)

        val result = scopeSearchManager.listScopes(ValueFilter.Matching(ScopeType.CONSENTABLE), null, firstPage)

        assertEquals(listOf(enabledScope), result.items.map { it.scope })
    }

    @Test
    fun `listScopes - Keep no scope when the type criterion matches nothing`() = runTest {
        coEvery { scopeManager.listAllScopes() } returns listOf(enabledScope, grantableScope)

        val result = scopeSearchManager.listScopes(ValueFilter.MatchesNothing, null, firstPage)

        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `listScopes - Keep the scopes the deployment serves`() = runTest {
        knownScopes(enabledScope, disabledScope)

        val result = scopeSearchManager.listScopes(ValueFilter.Unfiltered, true, firstPage)

        assertEquals(listOf(enabledScope), result.items.map { it.scope })
    }

    @Test
    fun `listScopes - Keep the scopes the deployment turned off`() = runTest {
        knownScopes(enabledScope, disabledScope)

        val result = scopeSearchManager.listScopes(ValueFilter.Unfiltered, false, firstPage)

        assertEquals(listOf(disabledScope), result.items.map { it.scope })
    }

    @Test
    fun `listScopes - Keep the scopes both criteria name`() = runTest {
        knownScopes(enabledScope, disabledScope, grantableScope)

        val result = scopeSearchManager.listScopes(ValueFilter.Matching(ScopeType.CONSENTABLE), true, firstPage)

        assertEquals(listOf(enabledScope), result.items.map { it.scope })
    }

    @Test
    fun `listScopes - Answer each scope with the claims it protects`() = runTest {
        val gatedClaim = mockk<Claim>()
        coEvery { scopeManager.listAllScopes() } returns listOf(enabledScope, grantableScope)
        coEvery { scopeManager.listClaimsProtectedByScope(enabledScope) } returns listOf(gatedClaim)
        coEvery { scopeManager.listClaimsProtectedByScope(grantableScope) } returns emptyList()

        val result = scopeSearchManager.listScopes(ValueFilter.Unfiltered, null, firstPage)

        assertEquals(listOf(gatedClaim), result.items.first { it.scope == enabledScope }.protectedClaims)
        assertTrue(result.items.first { it.scope == grantableScope }.protectedClaims.isEmpty())
    }

    @Test
    fun `listScopes - Order by scope before slicing`() = runTest {
        // Handed last-first, the first page of two still holds the two scopes the order puts first.
        knownScopes(enabledScope, grantableScope, disabledScope)

        val result = scopeSearchManager.listScopes(ValueFilter.Unfiltered, null, PageParams(page = 0, size = 2))

        assertEquals(listOf(disabledScope, grantableScope), result.items.map { it.scope })
    }

    @Test
    fun `listScopes - Return the page the parameters name, out of everything the criteria kept`() = runTest {
        knownScopes(enabledScope, grantableScope, disabledScope)

        val result = scopeSearchManager.listScopes(ValueFilter.Unfiltered, null, PageParams(page = 1, size = 2))

        assertEquals(listOf(enabledScope), result.items.map { it.scope })
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(3, result.total)
    }

    private fun knownScopes(vararg scopes: Scope) {
        coEvery { scopeManager.listAllScopes() } returns scopes.toList()
        coEvery { scopeManager.listClaimsProtectedByScope(any()) } returns emptyList()
    }
}
