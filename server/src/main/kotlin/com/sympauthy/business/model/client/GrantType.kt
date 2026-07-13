package com.sympauthy.business.model.client

enum class GrantType(val value: String) {
    AUTHORIZATION_CODE("authorization_code"),
    REFRESH_TOKEN("refresh_token"),
    CLIENT_CREDENTIALS("client_credentials"),

    /**
     * OAuth 2.0 Token Exchange (RFC 8693). Used by a confidential client to obtain an access token that acts on
     * behalf of a user (delegation, recorded via the `act` claim).
     */
    TOKEN_EXCHANGE("urn:ietf:params:oauth:grant-type:token-exchange");

    companion object {
        fun fromValueOrNull(value: String?): GrantType? =
            value?.let { v -> entries.firstOrNull { it.value == v } }
    }
}
