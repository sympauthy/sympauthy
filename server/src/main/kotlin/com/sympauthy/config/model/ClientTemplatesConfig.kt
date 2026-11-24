package com.sympauthy.config.model

import com.sympauthy.business.client.model.AuthorizationWebhook
import com.sympauthy.business.client.model.GrantType
import com.sympauthy.business.flow.model.AuthorizationFlow
import com.sympauthy.business.scope.model.EnabledScope
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.exception.localizedExceptionOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

sealed class ClientTemplatesConfig(
    configurationErrors: List<ConfigurationException>? = null
) : Config(configurationErrors)

data class EnabledClientTemplatesConfig(
    val templates: Map<String, ClientTemplate>
) : ClientTemplatesConfig()

class DisabledClientTemplatesConfig(
    configurationErrors: List<ConfigurationException>
) : ClientTemplatesConfig(configurationErrors)

fun ClientTemplatesConfig.orThrow(): EnabledClientTemplatesConfig {
    return when (this) {
        is EnabledClientTemplatesConfig -> this
        is DisabledClientTemplatesConfig -> throw this.invalidConfig
    }
}

suspend fun Flow<ClientTemplatesConfig>.orThrow(): EnabledClientTemplatesConfig {
    val config = firstOrNull() ?: throw localizedExceptionOf(INVALID_CONFIG_DETAILS_ID)
    return config.orThrow()
}

suspend fun Flow<ClientTemplatesConfig>.orNull(): EnabledClientTemplatesConfig? {
    return (firstOrNull() as? EnabledClientTemplatesConfig)
}

/**
 * A validated client template holding default values for client configurations.
 *
 * All properties are nullable since a template only needs to define the values it wants to provide as defaults.
 */
data class ClientTemplate(
    val id: String,
    val audienceId: String?,
    val public: Boolean?,
    val allowedGrantTypes: Set<GrantType>?,
    val authorizationFlow: AuthorizationFlow?,
    val allowedRedirectUris: List<String>?,
    val returnUri: String?,
    val allowedReturnUris: List<String>?,
    val allowedScopes: Set<EnabledScope>?,
    val defaultScopes: List<EnabledScope>?,
    val authorizationWebhook: AuthorizationWebhook?
)
