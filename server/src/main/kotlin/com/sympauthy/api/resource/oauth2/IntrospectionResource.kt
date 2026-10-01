package com.sympauthy.api.resource.oauth2

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "IntrospectionResource",
    description = "Response from the token introspection endpoint per RFC 7662. When the token is inactive, only the 'active' field (set to false) is returned; all other fields are omitted. Beside the members declared here, the response carries the claims a deployment publishes in the introspection response, each under the identifier it was configured with.",
    externalDocs = ExternalDocumentation(
        url = "https://datatracker.ietf.org/doc/html/rfc7662#section-2.2"
    ),
    additionalProperties = Schema.AdditionalPropertiesValue.TRUE
)
@Serdeable
@Suppress("MaxLineLength")
data class IntrospectionResource(
    @get:Schema(
        description = "Whether the token is active. A token is active if it has been issued by this server, has not expired, and has not been revoked. When false, all other fields in the response will be omitted.",
        required = true
    )
    val active: Boolean,

    @get:Schema(
        description = "Space-separated list of scopes associated with the token. Only present when active is true."
    )
    val scope: String? = null,

    @get:Schema(description = "Client identifier for the OAuth 2.0 client that requested this token.")
    @get:JsonProperty("client_id")
    val clientId: String? = null,

    @get:Schema(description = "Human-readable identifier for the resource owner who authorized this token. Not returned for client_credentials tokens.")
    val username: String? = null,

    @get:Schema(description = """Type of the token, either "Bearer" or "DPoP".""", allowableValues = ["Bearer", "DPoP"])
    @get:JsonProperty("token_type")
    val tokenType: String? = null,

    @get:Schema(description = "Expiration time of the token as a Unix timestamp (seconds since epoch).")
    val exp: Long? = null,

    @get:Schema(description = "Time at which the token was issued as a Unix timestamp (seconds since epoch).")
    val iat: Long? = null,

    @get:Schema(description = "Time at which the end-user proved a credential of their account, as a Unix timestamp (seconds since epoch). Absent for a token no end-user authentication is behind: a client_credentials token, or one obtained through token exchange.")
    @get:JsonProperty("auth_time")
    val authTime: Long? = null,

    @get:Schema(description = "Subject of the token. For user tokens this is the user ID; for client_credentials tokens this is the client ID.")
    val sub: String? = null,

    @get:Schema(description = "Intended audience of the token.")
    val aud: String? = null,

    @get:Schema(description = "Issuer of the token (this authorization server).")
    val iss: String? = null,

    @get:Schema(description = "Unique identifier of the token.")
    val jti: String? = null,

    /**
     * The claims of the person this token was issued for that the deployment publishes in the
     * introspection response, each under the identifier it was configured with, serialized beside the
     * members above rather than under a member of its own.
     *
     * Hidden from the schema because it is not a member a client ever reads: what it holds is declared on
     * the class as additional properties, which is what the response actually carries.
     */
    @get:Schema(hidden = true)
    @get:JsonAnyGetter
    val additionalClaims: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * The JSON members this resource declares, and therefore the names [additionalClaims] may not be
         * written under: a value published twice under one name is a response whose reader picks.
         *
         * `username` is here although nothing answers it yet, because what the set is for is the contract
         * this resource publishes rather than what one response happens to fill in.
         */
        val DECLARED_MEMBERS: Set<String> = setOf(
            "active", "scope", "client_id", "username", "token_type",
            "exp", "iat", "auth_time", "sub", "aud", "iss", "jti"
        )
    }
}
