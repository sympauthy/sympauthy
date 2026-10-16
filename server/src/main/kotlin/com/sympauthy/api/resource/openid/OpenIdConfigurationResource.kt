package com.sympauthy.api.resource.openid

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Metadata of this server, as OpenID Connect Discovery 1.0 §3 defines it. A member naming a " +
        "feature this server does not support is absent, unless its default when absent would say the feature " +
        "is supported.",
    externalDocs = ExternalDocumentation(
        description = "OpenID Connect Discovery 1.0, §3",
        url = "https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata"
    )
)
@Serdeable
data class OpenIdConfigurationResource(
    @get:Schema(
        description = "Identifier of this server, which every token it issues states as `iss`. Configured by " +
            "`auth.issuer`."
    )
    @get:JsonProperty("issuer")
    val issuer: String,
    @get:Schema(description = "URL of the authorization endpoint.")
    @get:JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    @get:Schema(description = "URL of the token endpoint.")
    @get:JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @get:Schema(description = "URL of the userinfo endpoint.")
    @get:JsonProperty("userinfo_endpoint")
    val userInfoEndpoint: String? = null,
    @get:Schema(
        description = "URL of the key set holding the public keys that verify the id tokens and access tokens " +
            "this server signs."
    )
    @get:JsonProperty("jwks_uri")
    val jwksUri: String,
    @get:Schema(
        description = "URL of the dynamic client registration endpoint. Always absent: clients are registered " +
            "in the configuration."
    )
    @get:JsonProperty("registration_endpoint")
    val registrationEndpoint: String? = null,
    @get:Schema(description = "URL of the revocation endpoint, RFC 8414 §2.")
    @get:JsonProperty("revocation_endpoint")
    val revocationEndpoint: String? = null,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Client authentication methods the revocation endpoint accepts, RFC 8414 §2. `none` " +
                "is a public client sending its `client_id` alone."
        ),
        schema = Schema(allowableValues = ["client_secret_basic", "client_secret_post", "none"])
    )
    @get:JsonProperty("revocation_endpoint_auth_methods_supported")
    val revocationEndpointAuthMethodsSupported: List<String>? = null,
    @get:Schema(description = "URL of the introspection endpoint, RFC 8414 §2.")
    @get:JsonProperty("introspection_endpoint")
    val introspectionEndpoint: String? = null,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Client authentication methods the introspection endpoint accepts, RFC 8414 §2."
        ),
        schema = Schema(allowableValues = ["client_secret_basic", "client_secret_post"])
    )
    @get:JsonProperty("introspection_endpoint_auth_methods_supported")
    val introspectionEndpointAuthMethodsSupported: List<String>? = null,
    @get:Schema(
        description = "Scopes the configuration publishes in discovery (`scopes.<id>.published-in`). A scope " +
            "left out of this list is still served."
    )
    @get:JsonProperty("scopes_supported")
    val scopesSupported: List<String>? = null,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "`response_type` values the authorization endpoint accepts."
        ),
        schema = Schema(allowableValues = ["code"])
    )
    @get:JsonProperty("response_types_supported")
    val responseTypesSupported: List<String>,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "`response_mode` values the authorization endpoint answers with. A `response_mode` in " +
                "the authorization request is ignored, and the authorization response is sent in the query " +
                "string of `redirect_uri`."
        ),
        schema = Schema(allowableValues = ["query"])
    )
    @get:JsonProperty("response_modes_supported")
    val responseModesSupported: List<String>? = null,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "`grant_type` values the token endpoint accepts."
        ),
        schema = Schema(
            allowableValues = [
                "authorization_code",
                "refresh_token",
                "client_credentials",
                "urn:ietf:params:oauth:grant-type:token-exchange"
            ]
        )
    )
    @get:JsonProperty("grant_types_supported")
    val grantTypesSupported: List<String>? = null,
    @get:Schema(
        description = "Authentication context class references this server supports. Always absent: this " +
            "server states none."
    )
    @get:JsonProperty("acr_values_supported")
    val acrValuesSupported: List<String>? = null,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Subject identifier types this server issues. Every client is given the same `sub` for a " +
                "user."
        ),
        schema = Schema(allowableValues = ["public"])
    )
    @get:JsonProperty("subject_types_supported")
    val subjectTypesSupported: List<String>,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Algorithms signing the id tokens: the one `advanced.jwt.public-alg` configures."
        ),
        schema = Schema(allowableValues = ["RS256", "PS256", "ES256"])
    )
    @get:JsonProperty("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>,
    @get:Schema(
        description = "Algorithms encrypting the id tokens. Always absent: this server does not encrypt them."
    )
    @get:JsonProperty("id_token_encryption_alg_values_supported")
    val idTokenEncryptionAlgValuesSupported: List<String>? = null,
    @get:Schema(
        description = "Content encryption algorithms of the id tokens. Always absent: this server does not " +
            "encrypt them."
    )
    @get:JsonProperty("id_token_encryption_enc_values_supported")
    val idTokenEncryptionEncValuesSupported: List<String>? = null,
    @get:Schema(
        description = "Algorithms signing the userinfo response. Always absent: the userinfo response is plain " +
            "JSON."
    )
    @get:JsonProperty("userinfo_signing_alg_values_supported")
    val userInfoSigningAlgValuesSupported: List<String>? = null,
    @get:Schema(
        description = "Algorithms encrypting the userinfo response. Always absent: the userinfo response is " +
            "plain JSON."
    )
    @get:JsonProperty("userinfo_encryption_alg_values_supported")
    val userInfoEncryptionAlgValuesSupported: List<String>? = null,
    @get:Schema(
        description = "Content encryption algorithms of the userinfo response. Always absent: the userinfo " +
            "response is plain JSON."
    )
    @get:JsonProperty("userinfo_encryption_enc_values_supported")
    val userInfoEncryptionEncValuesSupported: List<String>? = null,
    @get:Schema(
        description = "Algorithms a request object may be signed with. Always absent: this server does not " +
            "accept request objects."
    )
    @get:JsonProperty("request_object_signing_alg_values_supported")
    val requestObjectSigningAlgValuesSupported: List<String>? = null,
    @get:Schema(
        description = "Algorithms a request object may be encrypted with. Always absent: this server does not " +
            "accept request objects."
    )
    @get:JsonProperty("request_object_encryption_alg_values_supported")
    val requestObjectEncryptionAlgValuesSupported: List<String>? = null,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Client authentication methods the token endpoint accepts. `none` is a public client " +
                "sending its `client_id` alone, which the `authorization_code` and `refresh_token` grants accept."
        ),
        schema = Schema(allowableValues = ["client_secret_basic", "client_secret_post", "none"])
    )
    @get:JsonProperty("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>? = null,
    @get:Schema(
        description = "Algorithms a client may sign a JWT authenticating it at the token endpoint with. Always " +
            "absent: this server does not accept such a JWT."
    )
    @get:JsonProperty("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgValuesSupported: List<String>? = null,
    @get:Schema(
        description = "`display` values the authorization endpoint supports. Always absent: this server reads " +
            "no `display`."
    )
    @get:JsonProperty("display_values_supported")
    val displayValuesSupported: List<String>? = null,
    @get:Schema(
        description = "Claim types this server supports. Always absent: every claim is a normal claim."
    )
    @get:JsonProperty("claim_types_supported")
    val claimTypesSupported: List<String>? = null,
    @get:Schema(
        description = "Claims the configuration publishes in discovery (`claims.<id>.published-in`), each with " +
            "its `_verified` companion where it has one. A claim left out of this list is still served."
    )
    @get:JsonProperty("claims_supported")
    val claimsSupported: List<String>? = null,
    @get:Schema(
        description = "URL of documentation for developers. Always absent: this server publishes none here."
    )
    @get:JsonProperty("service_documentation")
    val serviceDocumentation: String? = null,
    @get:Schema(
        description = "Languages the values of claims may be returned in. Always absent: this server returns " +
            "claim values in no particular language."
    )
    @get:JsonProperty("claims_locales_supported")
    val claimsLocalesSupported: List<String>? = null,
    @get:Schema(
        description = "Languages the UI may be displayed in. Always absent: this server reads no `ui_locales`."
    )
    @get:JsonProperty("ui_locales_supported")
    val uiLocalesSupported: List<String>? = null,
    @get:Schema(
        description = "Whether the authorization endpoint accepts the `claims` parameter. Always true."
    )
    @get:JsonProperty("claims_parameter_supported")
    val claimsParameterSupported: Boolean? = null,
    @get:Schema(
        description = "Whether the authorization endpoint accepts the `request` parameter. Always absent: it " +
            "does not."
    )
    @get:JsonProperty("request_parameter_supported")
    val requestParameterSupported: Boolean? = null,
    @get:Schema(
        description = "Whether the authorization endpoint accepts the `request_uri` parameter. Always false."
    )
    @get:JsonProperty("request_uri_parameter_supported")
    val requestUriParameterSupported: Boolean? = null,
    @get:Schema(
        description = "Whether a client must register its `request_uri` values. Always absent: this server " +
            "accepts no `request_uri`."
    )
    @get:JsonProperty("require_request_uri_registration")
    val requireRequestUriRegistration: Boolean? = null,
    @get:Schema(
        description = "URL of the policy on how a client may use the data this server provides. Always absent: " +
            "this server publishes none."
    )
    @get:JsonProperty("op_policy_uri")
    val opPolicyUri: String? = null,
    @get:Schema(
        description = "URL of the terms of service of this server. Always absent: this server publishes none."
    )
    @get:JsonProperty("op_tos_uri")
    val opTosUri: String? = null,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "PKCE code challenge methods the authorization endpoint accepts, RFC 8414 §2."
        ),
        schema = Schema(allowableValues = ["S256"])
    )
    @get:JsonProperty("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String>? = null,
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Algorithms a DPoP proof sent to the token endpoint may be signed with, RFC 9449 §5.1."
        ),
        schema = Schema(
            allowableValues = [
                "RS256",
                "RS384",
                "RS512",
                "ES256",
                "ES384",
                "ES512",
                "PS256",
                "PS384",
                "PS512"
            ]
        )
    )
    @get:JsonProperty("dpop_signing_alg_values_supported")
    val dpopSigningAlgValuesSupported: List<String>? = null
)
