package com.sympauthy.security

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.business.model.oauth2.isAdmin
import com.sympauthy.business.model.token.AuthenticationToken
import com.sympauthy.business.model.user.claim.ClaimAclContext
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
     * The client the [authenticationToken] was issued to, as the configuration declares it now.
     */
    val client: Client,
    /**
     * Scopes obtained through user consent (e.g., profile, email).
     */
    val consentedScopes: List<EnabledScope>,
    /**
     * Scopes granted through granting rules or auto-granted (e.g., openid, admin scopes).
     */
    val grantedScopes: List<EnabledScope>
) : Authentication {

    /**
     * The context the person's token is evaluated in at a door of a claim's ACL: the scopes they consented
     * to and the ones a rule granted, as this authentication resolved them, and when they authenticated.
     */
    fun claimAclContext() = ClaimAclContext(
        client = client,
        consentedScopes = consentedScopes.map { it.scope },
        grantedScopes = grantedScopes.map { it.scope },
        authenticationDate = authenticationToken.grant.authenticationDate,
        bound = authenticationToken.dpopJkt != null
    )

    override fun getName(): String = authenticationToken.grant.userId?.toString()
        ?: throw httpExceptionOf(FORBIDDEN, "authentication.no_user", "description.authentication.no_user")

    override fun getAttributes(): Map<String, Any> = emptyMap()

    override fun getRoles(): Collection<String> {
        val roles = mutableListOf(IS_USER)
        val adminScopes = grantedScopes.filter { it.isAdmin }
        if (adminScopes.isNotEmpty()) {
            roles.add(IS_ADMIN)
            adminScopes.forEach { roles.add("SCOPE_${it.scope}") }
        }
        return roles
    }
}

/**
 * Downcast the [Authentication] to a [UserAuthentication].
 * Throws a [FORBIDDEN] if the downcast is not possible meaning the authentication does not contains a user.
 */
val Authentication.userAuthentication: UserAuthentication
    get() = when (this) {
        is UserAuthentication -> this
        else -> throw httpExceptionOf(FORBIDDEN, "authentication.wrong", "description.authentication.wrong")
    }

val Authentication.consentedScopes: List<EnabledScope>
    get() = this.userAuthentication.consentedScopes

val Authentication.grantedScopes: List<EnabledScope>
    get() = this.userAuthentication.grantedScopes

val Authentication.userId: UUID
    get() = this.userAuthentication.authenticationToken.grant.userId
        ?: throw httpExceptionOf(FORBIDDEN, "authentication.no_user", "description.authentication.no_user")

