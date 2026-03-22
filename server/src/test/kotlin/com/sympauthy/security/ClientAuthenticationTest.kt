package com.sympauthy.security

import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.security.SecurityRule.IS_CLIENT
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClientAuthenticationTest {

    private fun createAuthentication(scopes: List<Scope>): ClientAuthentication {
        val token = mockk<AuthenticationToken> {
            every { clientId } returns "test-client"
        }
        return ClientAuthentication(authenticationToken = token, scopes = scopes)
    }

    @Test
    fun `getName - Returns clientId`() {
        val auth = createAuthentication(emptyList())
        assertEquals("test-client", auth.name)
    }

    @Test
    fun `getRoles - Returns IS_CLIENT with no scopes`() {
        val auth = createAuthentication(emptyList())
        val roles = auth.roles.toList()
        assertEquals(listOf(IS_CLIENT), roles)
    }

    @Test
    fun `getRoles - Returns IS_CLIENT and per-scope roles for client scopes`() {
        val auth = createAuthentication(
            listOf(
                ClientScope(scope = BuiltInClientScopeId.USERS_READ),
                ClientScope(scope = BuiltInClientScopeId.USERS_CLAIMS_READ)
            )
        )
        val roles = auth.roles.toList()
        assertTrue(roles.contains(IS_CLIENT))
        assertTrue(roles.contains("SCOPE_${BuiltInClientScopeId.USERS_READ}"))
        assertTrue(roles.contains("SCOPE_${BuiltInClientScopeId.USERS_CLAIMS_READ}"))
        assertEquals(3, roles.size)
    }
}
