package com.sympauthy.api.resource.oauth2

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "IntrospectionResource",
    description = "Information about a token, as RFC 7662 §2.2 defines it. An inactive token is answered with " +
        "`active` alone. Beside the members declared here, the answer for an active token issued for an " +
        "end-user carries the claims the configuration publishes in the introspection response " +
        "(`claims.<id>.published-in`), each under its identifier.",
    externalDocs = ExternalDocumentation(
        url = "https://datatracker.ietf.org/doc/html/rfc7662#section-2.2"
    ),
    additionalProperties = Schema.AdditionalPropertiesValue.TRUE
)
@Serdeable
data class IntrospectionResource(
    @get:Schema(
        description = "Whether the token is active: issued by this server to the calling client, not expired " +
            "and not revoked.",
        required = true
    )
    val active: Boolean,

    @get:Schema(
        description = "Scopes the token carries, separated by spaces. Absent when the token is inactive or " +
            "carries no scope."
    )
    val scope: String? = null,

    @get:Schema(
        description = "Identifier of the client the token was issued to. Absent when the token is inactive."
    )
    @get:JsonProperty("client_id")
    val clientId: String? = null,

    @get:Schema(
        description = "Human-readable identifier of the end-user the token was issued for. Always absent: this " +
            "server does not send it."
    )
    val username: String? = null,

    @get:Schema(
        description = "Type of the token. `DPoP` when the token is bound to the key of a DPoP proof. Absent when " +
            "the token is inactive.",
        allowableValues = ["Bearer", "DPoP"]
    )
    @get:JsonProperty("token_type")
    val tokenType: String? = null,

    @get:Schema(
        description = "Time at which the token expires, in seconds since the Unix epoch. Absent when the token " +
            "is inactive or never expires."
    )
    val exp: Long? = null,

    @get:Schema(
        description = "Time at which the token was issued, in seconds since the Unix epoch. Absent when the " +
            "token is inactive."
    )
    val iat: Long? = null,

    @get:Schema(
        description = "Time at which the end-user signed in for the authorization the token comes from, in " +
            "seconds since the Unix epoch. Absent when the token is inactive, or no end-user signed in for it: " +
            "a token from the `client_credentials` or the token exchange grant."
    )
    @get:JsonProperty("auth_time")
    val authTime: Long? = null,

    @get:Schema(
        description = "Subject of the token: the identifier of the user it was issued for, or of the client " +
            "for a `client_credentials` token. Absent when the token is inactive."
    )
    val sub: String? = null,

    @get:Schema(
        description = "Audience the token is for: the token audience of the audience the client belongs to. " +
            "Absent when the token is inactive."
    )
    val aud: String? = null,

    @get:Schema(
        description = "Issuer of the token, as `auth.issuer` configures it. Absent when the token is inactive."
    )
    val iss: String? = null,

    @get:Schema(description = "Identifier of the token. Absent when the token is inactive.")
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
