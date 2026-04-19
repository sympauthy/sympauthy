package com.sympauthy.config.model

import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.Scope
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
    val config = firstOrNull() ?: throw localizedExceptionOf("config.invalid")
    return config.orThrow()
}

/**
 * A validated client template holding default values for client configurations.
 *
 * All properties are nullable since a template only needs to define the values it wants to provide as defaults.
 */
data class ClientTemplate(
    val id: String,
    val public: Boolean?,
    val allowedGrantTypes: Set<GrantType>?,
    val authorizationFlow: AuthorizationFlow?,
    val allowedRedirectUris: List<String>?,
    val allowedScopes: Set<Scope>?,
    val defaultScopes: List<Scope>?,
    val authorizationWebhook: AuthorizationWebhook?
)
