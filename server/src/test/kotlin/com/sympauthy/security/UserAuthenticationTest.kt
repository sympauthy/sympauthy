package com.sympauthy.security

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.security.SecurityRule.IS_ADMIN
import com.sympauthy.security.SecurityRule.IS_USER
import io.micronaut.http.HttpStatus
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(MockKExtension::class)
class UserAuthenticationTest {

    private fun createAuthentication(
        consentedScopes: List<com.sympauthy.business.model.oauth2.EnabledScope> = emptyList(),
        grantedScopes: List<com.sympauthy.business.model.oauth2.EnabledScope> = emptyList()
    ): UserAuthentication {
        return UserAuthentication(
            // Only the name of an authentication is read off its token, and nothing here asks for it.
            authenticationToken = mockk(),
            consentedScopes = consentedScopes,
            grantedScopes = grantedScopes
        )
    }

    @Test
    fun `getName - Returns the id of the authenticated user`() {
        val id = UUID.randomUUID()
        val token = mockk<AuthenticationToken> { every { userId } returns id }

        val auth = UserAuthentication(token, consentedScopes = emptyList(), grantedScopes = emptyList())

        assertEquals(id.toString(), auth.name)
    }

    @Test
    fun `getName - Throws 403 when the token is bound to no user`() {
        val token = mockk<AuthenticationToken> { every { userId } returns null }

        val auth = UserAuthentication(token, consentedScopes = emptyList(), grantedScopes = emptyList())

        val exception = assertThrows<LocalizedHttpException> { auth.name }

        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun `getRoles - Returns IS_USER when no admin scopes`() {
        val auth = createAuthentication(
            grantedScopes = listOf(GrantableUserScope(scope = "openid", discoverable = true))
        )
        val roles = auth.roles
        assertEquals(listOf(IS_USER), roles.toList())
    }

    @Test
    fun `getRoles - Returns IS_ADMIN and per-scope roles when admin scopes present`() {
        val auth = createAuthentication(
            consentedScopes = listOf(ConsentableUserScope(scope = "profile")),
            grantedScopes = listOf(
                GrantableUserScope(scope = "openid", discoverable = true),
                GrantableUserScope(scope = AdminScopeId.CONFIG_READ, discoverable = false),
                GrantableUserScope(scope = AdminScopeId.USERS_READ, discoverable = false)
            )
        )
        val roles = auth.roles.toList()
        assertTrue(roles.contains(IS_USER))
        assertTrue(roles.contains(IS_ADMIN))
        assertTrue(roles.contains("SCOPE_${AdminScopeId.CONFIG_READ}"))
        assertTrue(roles.contains("SCOPE_${AdminScopeId.USERS_READ}"))
        assertEquals(4, roles.size)
    }

    @Test
    fun `getRoles - Does not add IS_ADMIN when only non-admin scopes`() {
        val auth = createAuthentication(
            consentedScopes = listOf(ConsentableUserScope(scope = "profile")),
            grantedScopes = listOf(GrantableUserScope(scope = "openid", discoverable = true))
        )
        val roles = auth.roles.toList()
        assertFalse(roles.contains(IS_ADMIN))
        assertEquals(1, roles.size)
    }
}