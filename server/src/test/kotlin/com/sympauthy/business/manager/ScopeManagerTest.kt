package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.oauth2.AdminScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.isAdmin
import com.sympauthy.config.model.EnabledScopesConfig
import io.mockk.coEvery
import io.mockk.every
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

@ExtendWith(MockKExtension::class)
class ScopeManagerTest {

    @MockK
    lateinit var scopesConfig: EnabledScopesConfig

    @MockK
    lateinit var claimManager: ClaimManager

    @SpyK
    @InjectMockKs
    lateinit var scopeManager: ScopeManager


    @Test
    fun `findOrThrow - Find scope`() = runTest {
        val scope = "scope"
        val foundScope = mockk<Scope>()
        coEvery { scopeManager.find(scope) } returns foundScope
        val result = scopeManager.findOrThrow(scope)
        assertSame(foundScope, result)
    }

    @Test
    fun `findOrThrow - Throw if scope cannot be found`() = runTest {
        val scope = "scope"
        coEvery { scopeManager.find(scope) } throws mockk<BusinessException>()
        assertThrows<BusinessException> {
            scopeManager.findOrThrow(scope)
        }
    }

    @Test
    fun `findForClientOrThrow - Return scope when found and client has no allowedScopes`() = runTest {
        val scope = "openid"
        val foundScope = mockk<Scope>()
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { allowedScopes } returns null
        }

        coEvery { scopeManager.find(scope) } returns foundScope

        val result = scopeManager.findForClientOrThrow(client, scope)

        assertSame(foundScope, result)
    }


    @Test
    fun `findForClientOrThrow - Return scope when found and client allows it`() = runTest {
        val scope = "openid"
        val foundScope = mockk<Scope>()
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { allowedScopes } returns setOf(foundScope)
        }

        coEvery { scopeManager.find(scope) } returns foundScope

        val result = scopeManager.findForClientOrThrow(client, scope)

        assertSame(foundScope, result)
    }

    @Test
    fun `findForClientOrThrow - Throw when scope not found`() = runTest {
        val scope = "invalid"
        val client = mockk<com.sympauthy.business.model.client.Client>()

        coEvery { scopeManager.find(scope) } returns null

        assertThrows<BusinessException> {
            scopeManager.findForClientOrThrow(client, scope)
        }
    }

    @Test
    fun `findForClientOrThrow - Throw when scope not allowed by client`() = runTest {
        val scope = "openid"
        val foundScope = mockk<Scope>()
        val otherScope = mockk<Scope>()
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { allowedScopes } returns setOf(otherScope)
        }

        coEvery { scopeManager.find(scope) } returns foundScope

        assertThrows<BusinessException> {
            scopeManager.findForClientOrThrow(client, scope)
        }
    }

    @Test
    fun `parseRequestedScopes - Parse and return scopes allowed by client`() = runTest {
        val scopeOne = "openid"
        val scopeTwo = "profile"
        val foundScopeOne = mockk<Scope>()
        val foundScopeTwo = mockk<Scope>()
        val client = mockk<com.sympauthy.business.model.client.Client>()

        coEvery { scopeManager.findForClientOrThrow(client, scopeOne) } returns foundScopeOne
        coEvery { scopeManager.findForClientOrThrow(client, scopeTwo) } returns foundScopeTwo

        val result = scopeManager.parseRequestedScopes(client, "$scopeOne $scopeTwo")

        assertEquals(2, result.size)
        assertSame(foundScopeOne, result[0])
        assertSame(foundScopeTwo, result[1])
    }

    @Test
    fun `parseRequestedScopes - Parse scopes with whitespace`() = runTest {
        val scopeOne = "openid"
        val scopeTwo = "profile"
        val foundScopeOne = mockk<Scope>()
        val foundScopeTwo = mockk<Scope>()
        val client = mockk<com.sympauthy.business.model.client.Client>()

        coEvery { scopeManager.findForClientOrThrow(client, scopeOne) } returns foundScopeOne
        coEvery { scopeManager.findForClientOrThrow(client, scopeTwo) } returns foundScopeTwo

        val result = scopeManager.parseRequestedScopes(client, " $scopeOne  $scopeTwo ")

        assertEquals(2, result.size)
        assertSame(foundScopeOne, result[0])
        assertSame(foundScopeTwo, result[1])
    }

    @Test
    fun `parseRequestedScopes - Return default scopes uncheckedScopes is blank`() = runTest {
        val defaultScope = mockk<Scope>()
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { defaultScopes } returns listOf(defaultScope)
        }

        val result = scopeManager.parseRequestedScopes(client, "   ")

        assertEquals(listOf(defaultScope), result)
    }

    @Test
    fun `parseRequestedScopes - Return default scopes uncheckedScopes is null`() = runTest {
        val defaultScope = mockk<Scope>()
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { defaultScopes } returns listOf(defaultScope)
        }

        val result = scopeManager.parseRequestedScopes(client, null)

        assertEquals(listOf(defaultScope), result)
    }

    @Test
    fun `adminScopes - Contains all admin scopes`() {
        val adminScopes = scopeManager.adminScopes
        assertEquals(AdminScope.entries.size, adminScopes.size)
        AdminScope.entries.forEach { adminScope ->
            val scope = adminScopes.first { it.scope == adminScope.scope }
            assertTrue(scope.isAdmin)
            assertTrue(scope is GrantableUserScope)
            assertFalse(scope.discoverable)
        }
    }
}
