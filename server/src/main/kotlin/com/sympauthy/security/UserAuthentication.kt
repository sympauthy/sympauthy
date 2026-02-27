package com.sympauthy.security

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.security.SecurityRule.IS_ADMIN
import com.sympauthy.security.SecurityRule.IS_USER
import io.micronaut.http.HttpStatus.FORBIDDEN
import io.micronaut.security.authentication.Authentication
import java.util.*

/**
 * Represent the state of authentication of a user.
 */
class UserAuthentication(
    /**
     * The token used by the end-user to authorize its request.
     */
    val authenticationToken: AuthenticationToken,
    /**
     * List of scopes granted to the user during the authorization.
     */
    val scopes: List<Scope>
): Authentication {

    override fun getName(): String = authenticationToken.userId?.toString()
        ?: throw httpExceptionOf(FORBIDDEN, "authentication.no_user")

    override fun getAttributes(): Map<String, Any> = emptyMap()

    override fun getRoles(): Collection<String> {
        val roles = mutableListOf(IS_USER)
        if (scopes.any { it.admin }) {
            roles.add(IS_ADMIN)
        }
        return roles
    }
}

/**
 * Downcast the [Authentication] to a [UserAuthentication].
 * Throws a [FORBIDDEN] if the downcast is not possible meaning the authentication does not contains a user.
 */
val Authentication.userAuthentication: UserAuthentication
    get() = when(this) {
        is UserAuthentication -> this
        else -> throw httpExceptionOf(FORBIDDEN, "authentication.wrong")
    }

val Authentication.scopes: List<Scope>
    get() = this.userAuthentication.scopes

val Authentication.userId: UUID
    get() = this.userAuthentication.authenticationToken.userId
        ?: throw httpExceptionOf(FORBIDDEN, "authentication.no_user")

