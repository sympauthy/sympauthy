package com.sympauthy.business.model.provider.config

import com.sympauthy.business.model.provider.ProviderAuthType
import java.net.URI

sealed class ProviderAuthConfig(
    val type: ProviderAuthType
)

class ProviderOAuth2Config(
    val clientId: String,
    val clientSecret: String,
    val scopes: List<String>?,

    val authorizationUri: URI,

    val tokenUri: URI,
) : ProviderAuthConfig(ProviderAuthType.OAUTH2)

class ProviderOpenIdConnectConfig(
    val clientId: String,
    val clientSecret: String,
    val scopes: List<String>,

    val issuer: URI,

    val authorizationUri: URI,
    val tokenUri: URI,
    val jwksUri: URI,
) : ProviderAuthConfig(ProviderAuthType.OIDC)
