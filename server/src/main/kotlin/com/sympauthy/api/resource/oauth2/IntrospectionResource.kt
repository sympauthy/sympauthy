package com.sympauthy.api.resource.oauth2

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "IntrospectionResource",
    description = "Response from the token introspection endpoint per RFC 7662. When the token is inactive, only the 'active' field (set to false) is returned; all other fields are omitted.",
    externalDocs = ExternalDocumentation(
        url = "https://datatracker.ietf.org/doc/html/rfc7662#section-2.2"
    )
)
@Serdeable
data class IntrospectionResource(
    @get:Schema(
        description = "Whether the token is active. A token is active if it has been issued by this server, has not expired, and has not been revoked. When false, all other fields in the response will be omitted.",
        required = true
    )
    val active: Boolean,

    @get:Schema(description = "Space-separated list of scopes associated with the token. Only present when active is true.")
    val scope: String? = null,

    @get:Schema(description = "Client identifier for the OAuth 2.0 client that requested this token.")
    @get:JsonProperty("client_id")
    val clientId: String? = null,

    @get:Schema(description = "Human-readable identifier for the resource owner who authorized this token. Not returned for client_credentials tokens.")
    val username: String? = null,

    @get:Schema(description = "Type of the token, either \"Bearer\" or \"DPoP\".", allowableValues = ["Bearer", "DPoP"])
    @get:JsonProperty("token_type")
    val tokenType: String? = null,

    @get:Schema(description = "Expiration time of the token as a Unix timestamp (seconds since epoch).")
    val exp: Long? = null,

    @get:Schema(description = "Time at which the token was issued as a Unix timestamp (seconds since epoch).")
    val iat: Long? = null,

    @get:Schema(description = "Subject of the token. For user tokens this is the user ID; for client_credentials tokens this is the client ID.")
    val sub: String? = null,

    @get:Schema(description = "Intended audience of the token.")
    val aud: String? = null,

    @get:Schema(description = "Issuer of the token (this authorization server).")
    val iss: String? = null,

    @get:Schema(description = "Unique identifier of the token.")
    val jti: String? = null
)
