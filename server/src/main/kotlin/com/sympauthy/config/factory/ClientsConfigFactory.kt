package com.sympauthy.config.factory

import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClientTemplate
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.ClientsConfig
import com.sympauthy.config.model.DisabledClientsConfig
import com.sympauthy.config.model.EnabledClientsConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.config.properties.ClientConfigurationProperties
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.DEFAULT
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

@Factory
class ClientsConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val validator: ClientConfigFieldParser,
    @Inject private val clientTemplatesConfig: Flow<ClientTemplatesConfig>
) {

    @Singleton
    fun provideClients(
        propertiesList: List<ClientConfigurationProperties>
    ): Flow<ClientsConfig> {
        return flow {
            val errors = mutableListOf<ConfigurationException>()

            val templatesConfig = clientTemplatesConfig.first().orThrow()
            val templates = templatesConfig.templates

            val clients = propertiesList.mapNotNull { config ->
                val template = resolveTemplate(config, templates, errors)
                getClient(
                    properties = config,
                    template = template,
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

    private fun resolveTemplate(
        properties: ClientConfigurationProperties,
        templates: Map<String, ClientTemplate>,
        errors: MutableList<ConfigurationException>
    ): ClientTemplate? {
        val templateName = properties.template
        if (templateName != null) {
            if (templateName == DEFAULT) {
                errors.add(
                    configExceptionOf(
                        "$CLIENTS_KEY.${properties.id}.template",
                        "config.client.template.cannot_reference_default"
                    )
                )
                return null
            }
            val template = templates[templateName]
            if (template == null) {
                errors.add(
                    configExceptionOf(
                        "$CLIENTS_KEY.${properties.id}.template",
                        "config.client.template.not_found",
                        "template" to templateName,
                        "client" to properties.id,
                        "availableTemplates" to templates.keys.filter { it != DEFAULT }.joinToString(", ")
                    )
                )
                return null
            }
            return template
        }
        return templates[DEFAULT]
    }

    private suspend fun getClient(
        properties: ClientConfigurationProperties,
        template: ClientTemplate?,
        errors: MutableList<ConfigurationException>
    ): Client? {
        val clientErrors = mutableListOf<ConfigurationException>()
        val configKeyPrefix = "$CLIENTS_KEY.${properties.id}"

        val isPublic = parser.getBoolean(
            properties, "$configKeyPrefix.public",
            ClientConfigurationProperties::`public`
        ) ?: template?.public ?: false

        val secret = try {
            val value = parser.getString(
                properties, "$configKeyPrefix.secret",
                ClientConfigurationProperties::secret
            )
            if (!isPublic && value == null) {
                throw configExceptionOf("$configKeyPrefix.secret", "config.missing")
            }
            value
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val allowedGrantTypes = try {
            val rawGrantTypes = properties.allowedGrantTypes
            if (rawGrantTypes != null) {
                validator.getAllowedGrantTypes(
                    configKey = "$configKeyPrefix.allowed-grant-types",
                    allowedGrantTypes = rawGrantTypes,
                    errors = clientErrors
                )
            } else {
                template?.allowedGrantTypes ?: run {
                    validator.getAllowedGrantTypes(
                        configKey = "$configKeyPrefix.allowed-grant-types",
                        allowedGrantTypes = null,
                        errors = clientErrors
                    )
                }
            }
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val authorizationFlow = try {
            val flowId = properties.authorizationFlow
            if (flowId != null) {
                validator.getAuthorizationFlow(
                    key = "$configKeyPrefix.authorization-flow",
                    flowId = flowId
                )
            } else {
                template?.authorizationFlow
            }
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val allowedRedirectUris = if (allowedGrantTypes?.contains(GrantType.AUTHORIZATION_CODE) != false) {
            try {
                val rawRedirectUris = properties.allowedRedirectUris
                if (rawRedirectUris != null) {
                    validator.getAllowedRedirectUris(
                        configKey = "$configKeyPrefix.allowed-redirect-uris",
                        uris = properties.uris,
                        allowedRedirectUris = rawRedirectUris,
                        errors = clientErrors
                    )
                } else {
                    template?.allowedRedirectUris ?: run {
                        validator.getAllowedRedirectUris(
                            configKey = "$configKeyPrefix.allowed-redirect-uris",
                            uris = properties.uris,
                            allowedRedirectUris = null,
                            errors = clientErrors
                        )
                    }
                }
            } catch (e: ConfigurationException) {
                clientErrors.add(e)
                null
            }
        } else {
            val rawRedirectUris = properties.allowedRedirectUris
            if (!rawRedirectUris.isNullOrEmpty()) {
                clientErrors.add(
                    configExceptionOf(
                        "$configKeyPrefix.allowed-redirect-uris",
                        "config.client.allowed_redirect_uris.unnecessary"
                    )
                )
            }
            emptyList()
        }

        val allowedScopes = try {
            val rawScopes = properties.allowedScopes
            if (rawScopes != null) {
                validator.getScopes(
                    key = "$configKeyPrefix.allowed-scopes",
                    scopes = rawScopes,
                    errors = clientErrors
                )?.toSet()
            } else {
                template?.allowedScopes
            }
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val defaultScopes = try {
            val rawScopes = properties.defaultScopes
            if (rawScopes != null) {
                validator.getScopes(
                    key = "$configKeyPrefix.default-scopes",
                    scopes = rawScopes,
                    errors = clientErrors
                )
            } else {
                template?.defaultScopes
            }
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val authorizationWebhook = try {
            val webhookConfig = properties.authorizationWebhook
            if (webhookConfig != null) {
                validator.getAuthorizationWebhook(
                    configKey = "$configKeyPrefix.authorization-webhook",
                    webhookConfig = webhookConfig,
                    errors = clientErrors
                )
            } else {
                template?.authorizationWebhook
            }
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
}
