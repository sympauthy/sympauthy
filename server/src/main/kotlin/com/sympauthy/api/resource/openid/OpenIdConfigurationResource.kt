package com.sympauthy.api.resource.openid

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    externalDocs = ExternalDocumentation(
        description = "OpenId Connect Discovery specification",
        url = "https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata"
    )
)
@Serdeable
data class OpenIdConfigurationResource(
    @get:JsonProperty("issuer")
    val issuer: String,
    @get:JsonProperty("authorization_endpoint")
    val authorizationEndpoint: String,
    @get:JsonProperty("token_endpoint")
    val tokenEndpoint: String,
    @get:JsonProperty("userinfo_endpoint")
    val userInfoEndpoint: String? = null,
    @get:JsonProperty("jwks_uri")
    val jwksUri: String,
    @get:JsonProperty("registration_endpoint")
    val registrationEndpoint: String? = null,
    @get:JsonProperty("revocation_endpoint")
    val revocationEndpoint: String? = null,
    @get:JsonProperty("scopes_supported")
    val scopesSupported: List<String>? = null,
    @get:JsonProperty("response_types_supported")
    val responseTypesSupported: List<String>,
    @get:JsonProperty("response_modes_supported")
    val responseModesSupported: List<String>? = null,
    @get:JsonProperty("grant_types_supported")
    val grantTypesSupported: List<String>? = null,
    @get:JsonProperty("acr_values_supported")
    val acrValuesSupported: List<String>? = null,
    @get:JsonProperty("subject_types_supported")
    val subjectTypesSupported: List<String>,
    @get:JsonProperty("id_token_signing_alg_values_supported")
    val idTokenSigningAlgValuesSupported: List<String>,
    @get:JsonProperty("id_token_encryption_alg_values_supported")
    val idTokenEncryptionAlgValuesSupported: List<String>? = null,
    @get:JsonProperty("id_token_encryption_enc_values_supported")
    val idTokenEncryptionEncValuesSupported: List<String>? = null,
    @get:JsonProperty("userinfo_signing_alg_values_supported")
    val userInfoSigningAlgValuesSupported: List<String>? = null,
    @get:JsonProperty("userinfo_encryption_alg_values_supported")
    val userInfoEncryptionAlgValuesSupported: List<String>? = null,
    @get:JsonProperty("userinfo_encryption_enc_values_supported")
    val userInfoEncryptionEncValuesSupported: List<String>? = null,
    @get:JsonProperty("request_object_signing_alg_values_supported")
    val requestObjectSigningAlgValuesSupported: List<String>? = null,
    @get:JsonProperty("request_object_encryption_alg_values_supported")
    val requestObjectEncryptionAlgValuesSupported: List<String>? = null,
    @get:JsonProperty("token_endpoint_auth_methods_supported")
    val tokenEndpointAuthMethodsSupported: List<String>? = null,
    @get:JsonProperty("token_endpoint_auth_signing_alg_values_supported")
    val tokenEndpointAuthSigningAlgValuesSupported: List<String>? = null,
    @get:JsonProperty("display_values_supported")
    val displayValuesSupported: List<String>? = null,
    @get:JsonProperty("claim_types_supported")
    val claimTypesSupported: List<String>? = null,
    @get:JsonProperty("claims_supported")
    val claimsSupported: List<String>? = null,
    @get:JsonProperty("service_documentation")
    val serviceDocumentation: String? = null,
    @get:JsonProperty("claims_locales_supported")
    val claimsLocalesSupported: List<String>? = null,
    @get:JsonProperty("ui_locales_supported")
    val uiLocalesSupported: List<String>? = null,
    @get:JsonProperty("claims_parameter_supported")
    val claimsParameterSupported: Boolean? = null,
    @get:JsonProperty("request_parameter_supported")
    val requestParameterSupported: Boolean? = null,
    @get:JsonProperty("request_uri_parameter_supported")
    val requestUriParameterSupported: Boolean? = null,
    @get:JsonProperty("require_request_uri_registration")
    val requireRequestUriRegistration: Boolean? = null,
    @get:JsonProperty("op_policy_uri")
    val opPolicyUri: String? = null,
    @get:JsonProperty("op_tos_uri")
    val opTosUri: String? = null
)
