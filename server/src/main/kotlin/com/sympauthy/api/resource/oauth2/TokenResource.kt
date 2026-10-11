package com.sympauthy.api.resource.oauth2

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "TokenResource",
    description = "Tokens issued to the client, as RFC 6749 §5.1 defines the response.",
    externalDocs = ExternalDocumentation(
        url = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.1"
    )
)
@Serdeable
data class TokenResource(
    @get:Schema(description = "Access token issued to the client.")
    @get:JsonProperty("access_token")
    val accessToken: String,
    @get:Schema(
        description = "Type of the access token. `DPoP` when the request carried a DPoP proof, which binds the " +
            "access token to the key of that proof.",
        allowableValues = ["bearer", "DPoP"]
    )
    @get:JsonProperty("token_type")
    val tokenType: String,
    @get:Schema(
        description = "Number of seconds the access token is valid for from its issue, as " +
            "`auth.token.access-expiration` configures."
    )
    @get:JsonProperty("expires_in")
    val expiresIn: Int?,
    @get:Schema(description = "Scopes the access token carries, separated by spaces. Absent when it carries none.")
    @get:JsonProperty("scope")
    val scope: String? = null,
    @get:Schema(
        description = "Refresh token the client presents on the `refresh_token` grant. On that grant, the one " +
            "presented unless this server issued a new one. Absent on the `client_credentials` and token " +
            "exchange grants, and on the `authorization_code` grant when the client is not allowed the " +
            "`refresh_token` grant or `auth.token.refresh-enabled` is false."
    )
    @get:JsonProperty("refresh_token")
    val refreshToken: String? = null,
    @get:Schema(
        description = "Id token asserting the identity of the end-user. Absent unless the grant carries the " +
            "`openid` scope, which a `client_credentials` or token exchange grant never does."
    )
    @get:JsonProperty("id_token")
    val idToken: String? = null,
    @get:Schema(
        description = "Type of the token a token exchange issued, RFC 8693 §2.2.1. Absent unless `grant_type` is " +
            "`urn:ietf:params:oauth:grant-type:token-exchange`.",
        allowableValues = ["urn:ietf:params:oauth:token-type:access_token"]
    )
    @get:JsonProperty("issued_token_type")
    val issuedTokenType: String? = null
)
