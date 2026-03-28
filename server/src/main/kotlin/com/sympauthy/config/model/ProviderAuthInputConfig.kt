package com.sympauthy.config.model

import java.net.URI

/**
 * Validated provider authentication configuration from YAML.
 *
 * For OAuth2 providers, all endpoints are available directly from config.
 * For OpenID Connect providers, only the issuer is known at config time;
 * the actual endpoints are discovered at runtime by [com.sympauthy.business.manager.provider.ProviderManager].
 */
sealed class ProviderAuthInputConfig

class ProviderOAuth2InputConfig(
    val clientId: String,
    val clientSecret: String,
    val scopes: List<String>?,
    val authorizationUri: URI,
    val tokenUri: URI,
) : ProviderAuthInputConfig()

class ProviderOpenIdConnectInputConfig(
    val clientId: String,
    val clientSecret: String,
    val scopes: List<String>,
    val issuer: URI,
) : ProviderAuthInputConfig()
