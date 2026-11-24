package com.sympauthy.business.provider.oauth2.model

import com.sympauthy.business.provider.config.model.ProviderOAuth2Config
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
