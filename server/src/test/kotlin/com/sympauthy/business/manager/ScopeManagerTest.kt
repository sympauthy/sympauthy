package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.EnabledScopesConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class ScopeManagerTest {

    @MockK
    lateinit var authConfig: EnabledAuthConfig

    @MockK
    lateinit var scopesConfig: EnabledScopesConfig

    @SpyK
    @InjectMockKs
    lateinit var scopeManager: ScopeManager

    @Test
    fun `parseRequestScope - Parse request scopes`() = runTest {
        val scopeOne = "scope/one"
        val foundScopeOne = mockk<Scope> {
            every { scope } returns scopeOne
        }
        coEvery { scopeManager.find(scopeOne) } returns foundScopeOne

        val scopeTwo = "scope/tow"
        val foundScopeTwo = mockk<Scope> {
            every { scope } returns scopeTwo
        }
        coEvery { scopeManager.find(scopeTwo) } returns foundScopeTwo

        val result = scopeManager.parseRequestScope("$scopeOne     $scopeTwo")

        assertEquals(2, result?.count())
        assertSame(foundScopeOne, result?.getOrNull(0))
        assertSame(foundScopeTwo, result?.getOrNull(1))
    }

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

        val result = scopeManager.parseRequestedScopes(client, "$scopeOne,$scopeTwo")

        assertEquals(2, result.size)
        assertSame(foundScopeOne, result[0])
        assertSame(foundScopeTwo, result[1])
    }

    @Test
    fun `parseRequestedScopes - Throw when uncheckedScopes is null`() = runTest {
        val client = mockk<com.sympauthy.business.model.client.Client>()

        val exception = assertThrows<BusinessException> {
            scopeManager.parseRequestedScopes(client, null)
        }

        assertEquals("scope.parse_requested.missing", exception.detailsId)
    }

    @Test
    fun `parseRequestedScopes - Throw when uncheckedScopes is blank`() = runTest {
        val client = mockk<com.sympauthy.business.model.client.Client>()

        val exception = assertThrows<BusinessException> {
            scopeManager.parseRequestedScopes(client, "   ")
        }

        assertEquals("scope.parse_requested.missing", exception.detailsId)
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

        val result = scopeManager.parseRequestedScopes(client, " $scopeOne , $scopeTwo ")

        assertEquals(2, result.size)
        assertSame(foundScopeOne, result[0])
        assertSame(foundScopeTwo, result[1])
    }
}
