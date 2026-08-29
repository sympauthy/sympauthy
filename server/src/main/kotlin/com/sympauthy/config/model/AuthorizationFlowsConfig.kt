package com.sympauthy.config.model

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.config.exception.ConfigurationException

sealed class AuthorizationFlowsConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

class DisabledAuthorizationFlowsConfig(
    configurationErrors: List<ConfigurationException>
) : AuthorizationFlowsConfig(configurationErrors)

class EnabledAuthorizationFlowsConfig(
    /**
     * The interactive flow bundled with this authorization server, whose pages it serves itself.
     */
    val bundledFlow: InteractiveFlow,
    configuredFlows: List<AuthorizationFlow>
) : AuthorizationFlowsConfig() {

    /**
     * Every flow this authorization server serves, the bundled one first.
     */
    val flows: List<AuthorizationFlow> = listOf(bundledFlow) + configuredFlows
}

fun AuthorizationFlowsConfig.orThrow(): EnabledAuthorizationFlowsConfig {
    return when (this) {
        is EnabledAuthorizationFlowsConfig -> this
        is DisabledAuthorizationFlowsConfig -> throw this.invalidConfig
    }
}
