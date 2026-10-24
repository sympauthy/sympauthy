package com.sympauthy.security

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.business.model.oauth2.isClientScope
import com.sympauthy.business.model.user.claim.ClaimAclContext
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
     * The client the [authenticationToken] was issued to, as the configuration declares it now.
     */
    val client: Client,
    /**
     * List of client scopes granted to the client.
     */
    val scopes: List<EnabledScope>
) : Authentication {

    val clientId: String get() = authenticationToken.grant.clientId

    /**
     * The context this client's token is evaluated in at a door of a claim's ACL, acting on a person who
     * consented to [consentedScopes] for the client's audience.
     *
     * The scopes a rule granted are the client's own, and no person authenticated behind the token: it was
     * issued by the `client_credentials` grant.
     */
    fun claimAclContext(consentedScopes: List<String>) = ClaimAclContext(
        client = client,
        consentedScopes = consentedScopes,
        grantedScopes = scopes.map { it.scope },
        authenticationDate = null,
        bound = authenticationToken.dpopJkt != null
    )

    override fun getName(): String = authenticationToken.grant.clientId

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
