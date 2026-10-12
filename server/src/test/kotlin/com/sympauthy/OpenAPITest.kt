package com.sympauthy

import com.sympauthy.business.model.oauth2.AdminScope
import com.sympauthy.business.model.oauth2.BuiltInClientScope
import io.swagger.v3.oas.annotations.security.OAuthFlow
import io.swagger.v3.oas.annotations.security.OAuthFlows
import io.swagger.v3.oas.annotations.security.SecuritySchemes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Holds the scopes each security scheme offers to the enum declaring the scopes of its surface.
 */
class OpenAPITest {

    private fun scopesOf(scheme: String, flow: (OAuthFlows) -> OAuthFlow): Set<String> {
        val schemes = OpenAPI::class.java.getAnnotation(SecuritySchemes::class.java).value
        return flow(schemes.single { it.name == scheme }.flows).scopes.map { it.name }.toSet()
    }

    @Test
    fun `Every admin scope is offered by the admin scheme`() {
        assertEquals(
            AdminScope.entries.map { it.scope }.toSet(),
            scopesOf("admin") { it.authorizationCode }
        )
    }

    @Test
    fun `Every built-in client scope is offered by the client scheme`() {
        assertEquals(
            BuiltInClientScope.entries.map { it.scope }.toSet(),
            scopesOf("client") { it.clientCredentials }
        )
    }
}
