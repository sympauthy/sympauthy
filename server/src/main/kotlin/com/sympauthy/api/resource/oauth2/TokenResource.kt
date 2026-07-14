package com.sympauthy.api.resource.oauth2

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "TokenResource",
    description = "The tokens issued by this authorization server.",
    externalDocs = ExternalDocumentation(
        url = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.1"
    )
)
@Serdeable
data class TokenResource(
    @get:JsonProperty("access_token")
    val accessToken: String,
    @get:JsonProperty("token_type")
    val tokenType: String,
    @get:JsonProperty("expired_in")
    val expiredIn: Int?,
    @get:JsonProperty("scope")
    val scope: String? = null,
    @get:JsonProperty("refresh_token")
    val refreshToken: String? = null,
    @get:JsonProperty("id_token")
    val idToken: String? = null,
    /**
     * The token type identifier of the issued token, per OAuth 2.0 Token Exchange (RFC 8693).
     * Only set for responses to a token-exchange grant.
     */
    @get:JsonProperty("issued_token_type")
    val issuedTokenType: String? = null
)
