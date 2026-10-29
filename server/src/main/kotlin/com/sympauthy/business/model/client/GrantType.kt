package com.sympauthy.business.model.client

import com.sympauthy.util.PublishedUnderAnotherName

enum class GrantType(
    /**
     * Whether a public client, which sends its identifier alone and cannot authenticate, may use this grant
     * at the token endpoint.
     */
    val publicClientAllowed: Boolean,
    override val publishedName: String? = null
) : PublishedUnderAnotherName {
    AUTHORIZATION_CODE(publicClientAllowed = true),
    REFRESH_TOKEN(publicClientAllowed = true),
    CLIENT_CREDENTIALS(publicClientAllowed = false),

    /**
     * OAuth 2.0 Token Exchange (RFC 8693). Used by a confidential client to obtain an access token that acts on
     * behalf of a user (delegation, recorded via the `act` claim).
     *
     * The specification names this grant with a URN, which is the one grant type of the four whose
     * name does not spell what a client sends.
     */
    TOKEN_EXCHANGE(publicClientAllowed = false, publishedName = "urn:ietf:params:oauth:grant-type:token-exchange")
}
