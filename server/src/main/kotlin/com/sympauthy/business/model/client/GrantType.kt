package com.sympauthy.business.model.client

import com.sympauthy.util.PublishedUnderAnotherName
import com.sympauthy.util.wireName

enum class GrantType(
    override val publishedName: String? = null
) : PublishedUnderAnotherName {
    AUTHORIZATION_CODE,
    REFRESH_TOKEN,
    CLIENT_CREDENTIALS,

    /**
     * OAuth 2.0 Token Exchange (RFC 8693). Used by a confidential client to obtain an access token that acts on
     * behalf of a user (delegation, recorded via the `act` claim).
     *
     * The specification names this grant with a URN, which is the one grant type of the four whose
     * name does not spell what a client sends.
     */
    TOKEN_EXCHANGE("urn:ietf:params:oauth:grant-type:token-exchange");

    companion object {
        fun fromWireNameOrNull(wireName: String?): GrantType? =
            entries.firstOrNull { it.wireName == wireName }
    }
}
