package com.sympauthy.config.model

import com.sympauthy.business.claim.model.Claim
import com.sympauthy.config.exception.ConfigurationException

sealed class ClaimsConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledClaimsConfig(
    val claims: List<Claim>
) : ClaimsConfig()

class DisabledClaimsConfig(
    configurationErrors: List<ConfigurationException>
) : ClaimsConfig(configurationErrors)

fun ClaimsConfig.orThrow(): EnabledClaimsConfig {
    return when (this) {
        is EnabledClaimsConfig -> this
        is DisabledClaimsConfig -> throw this.invalidConfig
    }
}
