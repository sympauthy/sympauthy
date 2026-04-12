package com.sympauthy.config.factory

import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.manager.flow.AuthorizationFlowManager
import com.sympauthy.business.model.client.AuthorizationWebhook
import com.sympauthy.business.model.client.AuthorizationWebhookOnFailure
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigTemplateResolver
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClientsConfig
import com.sympauthy.config.model.DisabledClientsConfig
import com.sympauthy.config.model.EnabledClientsConfig
import com.sympauthy.config.model.EnabledUrlsConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.properties.ClientConfigurationProperties
import com.sympauthy.config.properties.ClientConfigurationProperties.AuthorizationWebhookConfig
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.DEFAULT
import io.micronaut.context.annotation.Factory
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ClientsConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val scopeManager: ScopeManager,
    @Inject private val authorizationFlowManager: AuthorizationFlowManager,
    @Inject private val urlsConfig: UrlsConfig,
    @Inject private val templateResolver: ConfigTemplateResolver
) {

    @Singleton
    fun provideClients(
        propertiesList: List<ClientConfigurationProperties>
    ): Flow<ClientsConfig> {
        return flow {
            val errors = mutableListOf<ConfigurationException>()

            val defaultConfig = propertiesList.firstOrNull { it.id == DEFAULT }

            val clients = propertiesList
                .filter { it.id != DEFAULT }
                .mapNotNull { config ->
                    getClient(
                        properties = config,
                        defaultProperties = defaultConfig,
                        errors = errors
                    )
                }

            val config = if (errors.isEmpty()) {
                EnabledClientsConfig(clients)
            } else {
                DisabledClientsConfig(errors)
            }
            emit(config)
        }
    }

    private suspend fun getClient(
        properties: ClientConfigurationProperties,
        defaultProperties: ClientConfigurationProperties?,
        errors: MutableList<ConfigurationException>
    ): Client? {
        val clientErrors = mutableListOf<ConfigurationException>()

        val isPublic = parser.getBoolean(
            properties, "$CLIENTS_KEY.${properties.id}.public",
            ClientConfigurationProperties::`public`
        ) ?: defaultProperties?.let {
            parser.getBoolean(
                it, "$CLIENTS_KEY.${properties.id}.public",
                ClientConfigurationProperties::`public`
            )
        } ?: false

        val secret = try {
            val value = parser.getString(
                properties, "$CLIENTS_KEY.${properties.id}.secret",
                ClientConfigurationProperties::secret
            )
            if (!isPublic && value == null) {
                throw configExceptionOf("$CLIENTS_KEY.${properties.id}.secret", "config.missing")
            }
            value
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val allowedGrantTypes = try {
            getAllowedGrantTypes(
                properties = properties,
                allowedGrantTypes = properties.allowedGrantTypes ?: defaultProperties?.allowedGrantTypes,
                errors = clientErrors
            )
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val authorizationFlow = try {
            getAuthorizationFlow(
                key = "$CLIENTS_KEY.${properties.id}.authorization-flow",
                flowId = properties.authorizationFlow ?: defaultProperties?.authorizationFlow
            )
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val allowedRedirectUris = if (allowedGrantTypes?.contains(GrantType.AUTHORIZATION_CODE) != false) {
            try {
                getAllowedRedirectUris(
                    properties = properties,
                    allowedRedirectUris = properties.allowedRedirectUris ?: defaultProperties?.allowedRedirectUris,
                    errors = clientErrors
                )
            } catch (e: ConfigurationException) {
                clientErrors.add(e)
                null
            }
        } else {
            val rawRedirectUris = properties.allowedRedirectUris ?: defaultProperties?.allowedRedirectUris
            if (!rawRedirectUris.isNullOrEmpty()) {
                clientErrors.add(
                    configExceptionOf(
                        "$CLIENTS_KEY.${properties.id}.allowed-redirect-uris",
                        "config.client.allowed_redirect_uris.unnecessary"
                    )
                )
            }
            emptyList()
        }

        val allowedScopes = try {
            getScopes(
                key = "$CLIENTS_KEY.${properties.id}.allowed-scopes",
                scopes = properties.allowedScopes ?: defaultProperties?.allowedScopes,
                errors = clientErrors
            )?.toSet()
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val defaultScopes = try {
            getScopes(
                key = "$CLIENTS_KEY.${properties.id}.default-scopes",
                scopes = properties.defaultScopes ?: defaultProperties?.defaultScopes,
                errors = clientErrors
            )
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val authorizationWebhook = try {
            getAuthorizationWebhook(
                properties = properties,
                webhookConfig = properties.authorizationWebhook ?: defaultProperties?.authorizationWebhook,
                errors = clientErrors
            )
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        return if (clientErrors.isEmpty()) {
            Client(
                id = properties.id,
                secret = secret,
                public = isPublic,
                allowedGrantTypes = allowedGrantTypes!!,
                authorizationFlow = authorizationFlow,
                allowedRedirectUris = allowedRedirectUris!!,
                allowedScopes = allowedScopes,
                defaultScopes = defaultScopes,
                authorizationWebhook = authorizationWebhook
            )
        } else {
            errors.addAll(clientErrors)
            null
        }
    }

    private fun buildTemplateContext(
        properties: ClientConfigurationProperties
    ): Map<String, String> {
        val context = mutableMapOf<String, String>()
        val enabledUrlsConfig = urlsConfig as? EnabledUrlsConfig
        if (enabledUrlsConfig != null) {
            context["urls.root"] = enabledUrlsConfig.root.toString()
        }
        properties.uris?.forEach { (key, value) ->
            context["client.uris.$key"] = value
        }
        return context
    }

    private fun getAllowedRedirectUris(
        properties: ClientConfigurationProperties,
        allowedRedirectUris: List<String>?,
        errors: MutableList<ConfigurationException>
    ): List<String>? {
        val configKey = "$CLIENTS_KEY.${properties.id}.allowed-redirect-uris"
        if (allowedRedirectUris.isNullOrEmpty()) {
            errors.add(configExceptionOf(configKey, "config.client.allowed_redirect_uris.missing"))
            return null
        }

        val listErrors = mutableListOf<ConfigurationException>()
        val templateContext = buildTemplateContext(properties)

        val resolvedUris = allowedRedirectUris.mapIndexedNotNull { index, uri ->
            val itemKey = "$configKey[$index]"
            try {
                val resolved = templateResolver.resolve(uri, templateContext, itemKey)
                val parsedUri = UriBuilder.of(resolved).build()
                if (parsedUri.scheme.isNullOrBlank()) {
                    throw configExceptionOf(itemKey, "config.invalid_url")
                }
                resolved
            } catch (e: ConfigurationException) {
                listErrors.add(e)
                null
            }
        }

        return if (listErrors.isEmpty()) {
            resolvedUris
        } else {
            errors.addAll(listErrors)
            null
        }
    }

    private fun getAllowedGrantTypes(
        properties: ClientConfigurationProperties,
        allowedGrantTypes: List<String>?,
        errors: MutableList<ConfigurationException>
    ): Set<GrantType>? {
        val configKey = "$CLIENTS_KEY.${properties.id}.allowed-grant-types"
        if (allowedGrantTypes.isNullOrEmpty()) {
            errors.add(
                configExceptionOf(
                    configKey, "config.client.allowed_grant_types.missing",
                    "supportedValues" to GrantType.entries.joinToString(", ") { it.value }
                )
            )
            return null
        }

        val grantTypeErrors = mutableListOf<ConfigurationException>()
        val parsed = allowedGrantTypes.mapIndexedNotNull { index, value ->
            val itemKey = "$configKey[$index]"
            val grantType = GrantType.fromValueOrNull(value)
            if (grantType == null) {
                grantTypeErrors.add(
                    configExceptionOf(
                        itemKey, "config.client.allowed_grant_types.invalid",
                        "grantType" to value,
                        "supportedValues" to GrantType.entries.joinToString(", ") { it.value }
                    )
                )
            }
            grantType
        }.toSet()

        if (grantTypeErrors.isNotEmpty()) {
            errors.addAll(grantTypeErrors)
            return null
        }

        if (GrantType.REFRESH_TOKEN in parsed && GrantType.AUTHORIZATION_CODE !in parsed) {
            errors.add(
                configExceptionOf(
                    configKey, "config.client.allowed_grant_types.refresh_token_requires_authorization_code"
                )
            )
            return null
        }

        return parsed
    }

    private fun getAuthorizationFlow(
        key: String,
        flowId: String?
    ): AuthorizationFlow? {
        return flowId?.let {
            authorizationFlowManager.findByIdOrNull(it) ?: throw configExceptionOf(
                "$key", "config.client.authorization_flow.invalid",
                "flow" to flowId
            )
        }
    }

    private fun getAuthorizationWebhook(
        properties: ClientConfigurationProperties,
        webhookConfig: AuthorizationWebhookConfig?,
        errors: MutableList<ConfigurationException>
    ): AuthorizationWebhook? {
        if (webhookConfig == null) {
            return null
        }

        val configKey = "$CLIENTS_KEY.${properties.id}.authorization-webhook"
        val webhookErrors = mutableListOf<ConfigurationException>()

        val url = try {
            parser.getAbsoluteUriOrThrow(
                webhookConfig, "$configKey.url",
                AuthorizationWebhookConfig::url
            )
        } catch (e: ConfigurationException) {
            webhookErrors.add(e)
            null
        }

        val secret = try {
            parser.getStringOrThrow(
                webhookConfig, "$configKey.secret",
                AuthorizationWebhookConfig::secret
            )
        } catch (e: ConfigurationException) {
            webhookErrors.add(e)
            null
        }

        val onFailure = try {
            parser.getEnum(
                webhookConfig, "$configKey.on-failure",
                AuthorizationWebhookOnFailure.DENY_ALL,
                AuthorizationWebhookConfig::onFailure
            )
        } catch (e: ConfigurationException) {
            webhookErrors.add(e)
            null
        }

        return if (webhookErrors.isEmpty()) {
            AuthorizationWebhook(
                url = url!!,
                secret = secret!!,
                onFailure = onFailure!!,
            )
        } else {
            errors.addAll(webhookErrors)
            null
        }
    }

    private suspend fun getScopes(
        key: String,
        scopes: List<String>?,
        errors: MutableList<ConfigurationException>
    ): List<Scope>? {
        val scopeErrors = mutableListOf<ConfigurationException>()

        val verifiedScopes = scopes?.mapIndexedNotNull { index, scope ->
            try {
                val verifiedScope = scopeManager.find(scope)
                if (verifiedScope == null) {
                    val error = configExceptionOf(
                        "$key[${index}]", "config.client.scope.invalid",
                        "scope" to scope
                    )
                    scopeErrors.add(error)
                }
                verifiedScope
            } catch (t: Throwable) {
                // We do not had the error to the list since it is most likely already caused by another configuration error
                null
            }
        }

        return if (scopeErrors.isEmpty()) {
            verifiedScopes
        } else {
            errors.addAll(scopeErrors)
            null
        }
    }

}
