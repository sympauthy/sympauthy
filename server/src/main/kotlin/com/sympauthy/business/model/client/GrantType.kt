package com.sympauthy.business.model.client

enum class GrantType(val value: String) {
    AUTHORIZATION_CODE("authorization_code"),
    REFRESH_TOKEN("refresh_token"),
    CLIENT_CREDENTIALS("client_credentials");

    companion object {
        fun fromValueOrNull(value: String?): GrantType? =
            value?.let { v -> entries.firstOrNull { it.value == v } }
    }
}
