package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.oauth2.AdminScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.isAdmin
import com.sympauthy.config.model.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
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
    lateinit var adminConfig: AdminConfig

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
        val foundScope = mockk<Scope> { every { audienceId } returns null }
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { allowedScopes } returns null
            every { audience } returns mockk { every { id } returns "test-audience" }
        }

        coEvery { scopeManager.find(scope) } returns foundScope

        val result = scopeManager.findForClientOrThrow(client, scope)

        assertSame(foundScope, result)
    }


    @Test
    fun `findForClientOrThrow - Return scope when found and client allows it`() = runTest {
        val scope = "openid"
        val foundScope = mockk<Scope> { every { audienceId } returns null }
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { allowedScopes } returns setOf(foundScope)
            every { audience } returns mockk { every { id } returns "test-audience" }
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
        val foundScope = mockk<Scope> { every { audienceId } returns null }
        val otherScope = mockk<Scope>()
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { allowedScopes } returns setOf(otherScope)
            every { audience } returns mockk { every { id } returns "test-audience" }
        }

        coEvery { scopeManager.find(scope) } returns foundScope

        assertThrows<BusinessException> {
            scopeManager.findForClientOrThrow(client, scope)
        }
    }

    @Test
    fun `findForClientOrThrow - Throw when scope audience does not match client audience`() = runTest {
        val scope = "admin:users:read"
        val foundScope = mockk<Scope> {
            every { audienceId } returns "admin"
            every { this@mockk.scope } returns scope
        }
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { audience } returns mockk { every { id } returns "default" }
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
        val foundScopeOne = mockk<Scope> { every { audienceId } returns null }
        val foundScopeTwo = mockk<Scope> { every { audienceId } returns null }
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { allowedScopes } returns setOf(foundScopeOne, foundScopeTwo)
        }

        coEvery { scopeManager.find(scopeOne) } returns foundScopeOne
        coEvery { scopeManager.find(scopeTwo) } returns foundScopeTwo

        val result = scopeManager.parseRequestedScopes(client, "$scopeOne $scopeTwo")

        assertEquals(2, result.size)
        assertSame(foundScopeOne, result[0])
        assertSame(foundScopeTwo, result[1])
    }

    @Test
    fun `parseRequestedScopes - Parse scopes with whitespace`() = runTest {
        val scopeOne = "openid"
        val scopeTwo = "profile"
        val foundScopeOne = mockk<Scope> { every { audienceId } returns null }
        val foundScopeTwo = mockk<Scope> { every { audienceId } returns null }
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { allowedScopes } returns setOf(foundScopeOne, foundScopeTwo)
        }

        coEvery { scopeManager.find(scopeOne) } returns foundScopeOne
        coEvery { scopeManager.find(scopeTwo) } returns foundScopeTwo

        val result = scopeManager.parseRequestedScopes(client, " $scopeOne  $scopeTwo ")

        assertEquals(2, result.size)
        assertSame(foundScopeOne, result[0])
        assertSame(foundScopeTwo, result[1])
    }

    @Test
    fun `parseRequestedScopes - Throw when scope audience does not match client audience`() = runTest {
        val scope = "admin:users:read"
        val foundScope = mockk<Scope> {
            every { audienceId } returns "admin"
            every { this@mockk.scope } returns scope
        }
        val client = mockk<com.sympauthy.business.model.client.Client> {
            every { audience } returns mockk { every { id } returns "default" }
        }

        coEvery { scopeManager.find(scope) } returns foundScope

        assertThrows<BusinessException> {
            scopeManager.parseRequestedScopes(client, scope)
        }
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
    fun `adminScopes - Contains all admin scopes with configured audienceId`() {
        val adminConfig = EnabledAdminConfig(enabled = true, integratedUi = true, audienceId = "admin")
        val manager = ScopeManager(scopesConfig, adminConfig, claimManager)

        val adminScopes = manager.adminScopes
        assertEquals(AdminScope.entries.size, adminScopes.size)
        AdminScope.entries.forEach { adminScope ->
            val scope = adminScopes.first { it.scope == adminScope.scope }
            assertTrue(scope.isAdmin)
            assertTrue(scope is GrantableUserScope)
            assertFalse(scope.discoverable)
            assertEquals("admin", scope.audienceId)
        }
    }

    @Test
    fun `adminScopes - Returns empty list when admin is disabled`() {
        val adminConfig = DisabledAdminConfig(emptyList())
        val manager = ScopeManager(scopesConfig, adminConfig, claimManager)

        val adminScopes = manager.adminScopes
        assertTrue(adminScopes.isEmpty())
    }

    @Test
    fun `listScopesForAudience - Excludes admin scopes from non-admin audience`() = runTest {
        val adminConfig = EnabledAdminConfig(enabled = true, integratedUi = true, audienceId = "admin")
        val manager = spyk(ScopeManager(scopesConfig, adminConfig, claimManager))

        coEvery { manager.listScopes() } returns manager.adminScopes

        val result = manager.listScopesForAudience("default")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listScopesForAudience - Includes admin scopes for admin audience`() = runTest {
        val adminConfig = EnabledAdminConfig(enabled = true, integratedUi = true, audienceId = "admin")
        val manager = spyk(ScopeManager(scopesConfig, adminConfig, claimManager))

        coEvery { manager.listScopes() } returns manager.adminScopes

        val result = manager.listScopesForAudience("admin")

        assertEquals(AdminScope.entries.size, result.size)
    }
}
