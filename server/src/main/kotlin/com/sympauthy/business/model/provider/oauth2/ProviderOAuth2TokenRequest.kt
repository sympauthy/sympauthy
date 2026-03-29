package com.sympauthy.business.model.provider.oauth2

import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import java.net.URI

/**
 * Business object containing all information to call the token endpoint and obtain access token from a third-party
 * provider.
 */
data class ProviderOAuth2TokenRequest(
    val oauth2: ProviderOAuth2Config,
    val authorizeCode: String,
    val redirectUri: URI
)
