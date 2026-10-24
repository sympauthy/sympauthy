package com.sympauthy.business.model.user.claim

import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.grant.Grant
import java.time.LocalDateTime

/**
 * What a door of a claim's ACL is evaluated against: the facts about the token a read or a write comes
 * through, and the consent behind it.
 *
 * Each field answers one function an ACL expression may call, and holds what that function finds nothing in
 * where the token has no such thing: a `client_credentials` token has no person, so no authentication date,
 * and a person's token carries no client scope.
 */
data class ClaimAclContext(
    /**
     * The client the token was issued to, which `CLIENT` answers for.
     */
    val client: Client,
    /**
     * The scopes the person consented to, for the audience the read or write is for.
     */
    val consentedScopes: List<String>,
    /**
     * The scopes a rule granted the token: the client's own on a `client_credentials` token, the grantable
     * ones on a person's.
     */
    val grantedScopes: List<String>,
    /**
     * When the person behind the token authenticated, or null where no person did.
     */
    val authenticationDate: LocalDateTime?,
    /**
     * Whether the token is bound to a key.
     */
    val bound: Boolean
) {

    companion object {

        /**
         * The context of a token issued under [grant] to [client], bound to the key whose thumbprint is
         * [dpopJkt], or a bearer token where it is null.
         */
        fun of(grant: Grant, client: Client, dpopJkt: String?) = ClaimAclContext(
            client = client,
            consentedScopes = grant.consentedScopes,
            grantedScopes = grant.grantedScopes + grant.clientScopes,
            authenticationDate = grant.authenticationDate,
            bound = dpopJkt != null
        )
    }
}
