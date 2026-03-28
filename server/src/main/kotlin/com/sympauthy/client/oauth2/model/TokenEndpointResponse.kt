package com.sympauthy.client.oauth2.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class TokenEndpointResponse(
    @get:JsonProperty("access_token")
    val accessToken: String,
    @get:JsonProperty("token_type")
    val tokenType: String,
    @get:JsonProperty("expires_in")
    val expiresIn: Int,
    @get:JsonProperty("refresh_token")
    val refreshToken: String? = null,
    @get:JsonProperty("scope")
    val scope: String? = null,
    @get:JsonProperty("id_token")
    val idToken: String? = null
)
