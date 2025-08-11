package com.sympauthy.business.manager.auth.oauth2

import com.sympauthy.business.manager.auth.AuthorizationManager
import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.manager.jwt.JwtManager
import com.sympauthy.business.mapper.AuthorizeAttemptMapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.data.repository.AuthorizeAttemptRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AuthorizeManagerTest {

    @MockK
    lateinit var authorizationManagerTest: AuthorizationManager

    @MockK
    lateinit var authorizationFlowManager: AuthorizationFlowManager

    @MockK
    lateinit var authorizeAttemptRepository: AuthorizeAttemptRepository

    @MockK
    lateinit var jwtManager: JwtManager

    @MockK
    lateinit var authorizeAttemptMapper: AuthorizeAttemptMapper

    @SpyK
    @InjectMockKs
    lateinit var authorizeManager: AuthorizeManager

    @Test
    fun `getAllowedScopesForClient - Return provided scopes when client has not allowed scopes`() {
        val scope = mockk<Scope>()
        val client = mockk<Client> {
            every { allowedScopes } returns null
        }

        val result = authorizeManager.getAllowedScopesForClient(client, listOf(scope))

        assertEquals(1, result.count())
        assertSame(scope, result.getOrNull(0))
    }

    @Test
    fun `getAllowedScopesForClient - Return default scopes when no scope are provided`() {
        val scope = mockk<Scope>()
        val client = mockk<Client> {
            every { defaultScopes } returns listOf(scope)
        }

        val result = authorizeManager.getAllowedScopesForClient(client, null)

        assertEquals(1, result.count())
        assertSame(scope, result.getOrNull(0))
    }

    @Test
    fun `getAllowedScopesForClient - Filter according to allowed scopes`() {
        val allowedScopeOne = "allowedScopeOne"
        val allowedScope = mockk<Scope> {
            every { scope } returns allowedScopeOne
        }
        val uncheckedScopeOne = mockk<Scope> {
            every { scope } returns allowedScopeOne
        }
        val uncheckedScopeTwo = mockk<Scope> {
            every { scope } returns "notAllowedScopeOne"
        }
        val client = mockk<Client> {
            every { allowedScopes } returns setOf(allowedScope)
        }

        val result = authorizeManager.getAllowedScopesForClient(
            client = client,
            uncheckedScopes = listOf(uncheckedScopeOne, uncheckedScopeTwo)
        )

        assertEquals(1, result.count())
        assertSame(uncheckedScopeOne, result.getOrNull(0))
    }
}
