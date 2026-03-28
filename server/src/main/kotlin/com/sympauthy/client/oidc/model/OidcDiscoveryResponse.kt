package com.sympauthy.client.oidc.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class OidcDiscoveryResponse(
    val issuer: String,
    @get:JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    @get:JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @get:JsonProperty("userinfo_endpoint")
    val userinfoEndpoint: String? = null,
    @get:JsonProperty("jwks_uri")
    val jwksUri: String,
    @get:JsonProperty("scopes_supported")
    val scopesSupported: List<String>? = null,
    @get:JsonProperty("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>? = null,
)
