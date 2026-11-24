package com.sympauthy.config.model

import com.sympauthy.business.provider.config.model.ProviderUserInfoConfig

/**
 * Validated provider configuration from YAML, before any runtime resolution (e.g. OpenID Connect discovery).
 */
data class ProviderConfig(
    val id: String,
    val name: String,
    val auth: ProviderAuthInputConfig,
    val userInfo: ProviderUserInfoConfig?
)
