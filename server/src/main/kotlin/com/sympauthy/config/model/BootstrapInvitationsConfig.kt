package com.sympauthy.config.model

import com.sympauthy.config.exception.ConfigurationException

sealed class BootstrapInvitationsConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledBootstrapInvitationsConfig(
    val invitations: List<BootstrapInvitation>
) : BootstrapInvitationsConfig()

class DisabledBootstrapInvitationsConfig(
    configurationErrors: List<ConfigurationException>
) : BootstrapInvitationsConfig(configurationErrors)

fun BootstrapInvitationsConfig.orThrow(): EnabledBootstrapInvitationsConfig {
    return when (this) {
        is EnabledBootstrapInvitationsConfig -> this
        is DisabledBootstrapInvitationsConfig -> throw this.invalidConfig
    }
}

data class BootstrapInvitation(
    val id: String,
    val audienceId: String,
    val urlTemplate: String?,
    /**
     * Claims to pre-assign to the user upon registration.
     * Keys are canonical claim IDs resolved against the claim configuration.
     */
    val claims: Map<String, String>?,
    val note: String?
)