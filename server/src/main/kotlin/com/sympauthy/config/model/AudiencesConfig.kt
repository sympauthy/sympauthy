package com.sympauthy.config.model

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.config.exception.ConfigurationException

sealed class AudiencesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledAudiencesConfig(
    val audiences: List<Audience>
) : AudiencesConfig()

class DisabledAudiencesConfig(
    configurationErrors: List<ConfigurationException>
) : AudiencesConfig(configurationErrors)

fun AudiencesConfig.orThrow(): EnabledAudiencesConfig {
    return when (this) {
        is EnabledAudiencesConfig -> this
        is DisabledAudiencesConfig -> throw this.invalidConfig
    }
}
