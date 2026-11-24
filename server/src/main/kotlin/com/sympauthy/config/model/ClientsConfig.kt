package com.sympauthy.config.model

import com.sympauthy.business.client.model.Client
import com.sympauthy.config.exception.ConfigurationException

sealed class ClientsConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledClientsConfig(
    val clients: List<Client>
) : ClientsConfig()

class DisabledClientsConfig(
    configurationErrors: List<ConfigurationException>
) : ClientsConfig(configurationErrors)

fun ClientsConfig.orThrow(): EnabledClientsConfig {
    return when (this) {
        is EnabledClientsConfig -> this
        is DisabledClientsConfig -> throw this.invalidConfig
    }
}
