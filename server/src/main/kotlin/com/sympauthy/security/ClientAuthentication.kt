package com.sympauthy.security

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.isClientScope
import com.sympauthy.security.SecurityRule.IS_CLIENT
import io.micronaut.http.HttpStatus.FORBIDDEN
import io.micronaut.security.authentication.Authentication

/**
 * Represent the state of authentication of a client using the client_credentials flow.
 * Unlike [UserAuthentication], this is not associated with any end-user.
 */
class ClientAuthentication(
    /**
     * The token used by the client to authorize its request.
     */
    val authenticationToken: AuthenticationToken,
    /**
     * List of client scopes granted to the client.
     */
    val scopes: List<Scope>
) : Authentication {

    val clientId: String get() = authenticationToken.clientId

    override fun getName(): String = authenticationToken.clientId

    override fun getAttributes(): Map<String, Any> = emptyMap()

    override fun getRoles(): Collection<String> {
        val roles = mutableListOf(IS_CLIENT)
        scopes.filter { it.isClientScope }.forEach {
            roles.add("SCOPE_${it.scope}")
        }
        return roles
    }
}

/**
 * Downcast the [Authentication] to a [ClientAuthentication].
 * Throws a [FORBIDDEN] if the downcast is not possible meaning the authentication does not represent a client.
 */
val Authentication.clientAuthentication: ClientAuthentication
    get() = when (this) {
        is ClientAuthentication -> this
        else -> throw httpExceptionOf(FORBIDDEN, "authentication.no_client", "description.authentication.no_client")
    }
