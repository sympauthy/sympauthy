package com.sympauthy.security

import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.security.SecurityRule.IS_ADMIN
import com.sympauthy.security.SecurityRule.IS_USER
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class UserAuthenticationTest {

    private fun createAuthentication(scopes: List<Scope>): UserAuthentication {
        val token = mockk<AuthenticationToken> {
            every { userId } returns UUID.randomUUID()
        }
        return UserAuthentication(authenticationToken = token, scopes = scopes)
    }

    @Test
    fun `getRoles - Returns IS_USER when no admin scopes`() {
        val auth = createAuthentication(
            listOf(Scope(scope = "openid", admin = false, discoverable = true))
        )
        val roles = auth.roles
        assertEquals(listOf(IS_USER), roles.toList())
    }

    @Test
    fun `getRoles - Returns IS_ADMIN and per-scope roles when admin scopes present`() {
        val auth = createAuthentication(
            listOf(
                Scope(scope = "openid", admin = false, discoverable = true),
                Scope(scope = AdminScopeId.CLIENTS_READ, admin = true, discoverable = false),
                Scope(scope = AdminScopeId.USERS_READ, admin = true, discoverable = false)
            )
        )
        val roles = auth.roles.toList()
        assertTrue(roles.contains(IS_USER))
        assertTrue(roles.contains(IS_ADMIN))
        assertTrue(roles.contains("SCOPE_${AdminScopeId.CLIENTS_READ}"))
        assertTrue(roles.contains("SCOPE_${AdminScopeId.USERS_READ}"))
        assertEquals(4, roles.size)
    }

    @Test
    fun `getRoles - Does not add IS_ADMIN when only non-admin scopes`() {
        val auth = createAuthentication(
            listOf(
                Scope(scope = "openid", admin = false, discoverable = true),
                Scope(scope = "profile", admin = false, discoverable = true)
            )
        )
        val roles = auth.roles.toList()
        assertFalse(roles.contains(IS_ADMIN))
        assertEquals(1, roles.size)
    }
}
