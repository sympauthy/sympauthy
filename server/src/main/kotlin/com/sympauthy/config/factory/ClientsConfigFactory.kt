package com.sympauthy.config.factory

import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.GrantType
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.ClientConfigurationProperties
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.DEFAULT
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ClientsConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val fieldParser: ClientConfigFieldParser,
    @Inject private val clientTemplatesConfig: Flow<ClientTemplatesConfig>
) {

    @Singleton
    fun provideClients(
        propertiesList: List<ClientConfigurationProperties>
    ): Flow<ClientsConfig> {
        return flow {
            val templatesConfig = clientTemplatesConfig.orNull()
            if (templatesConfig == null) {
                emit(DisabledClientsConfig(emptyList()))
                return@flow
            }
            val templates = templatesConfig.templates

            val errors = mutableListOf<ConfigurationException>()
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
            when {
                properties.allowedGrantTypes != null -> fieldParser.getAllowedGrantTypesOrNull(
                    configKey = "$configKeyPrefix.allowed-grant-types",
                    allowedGrantTypes = properties.allowedGrantTypes,
                    errors = clientErrors
                )

                template?.allowedGrantTypes != null -> template.allowedGrantTypes
                else -> {
                    clientErrors.add(
                        configExceptionOf(
                        "$configKeyPrefix.allowed-grant-types",
                        "config.client.allowed_grant_types.missing",
                        "supportedValues" to GrantType.entries.joinToString(", ") { it.value }
                    ))
                    null
                }
            }
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val authorizationFlow = try {
            when {
                properties.authorizationFlow != null -> fieldParser.getAuthorizationFlow(
                    key = "$configKeyPrefix.authorization-flow",
                    flowId = properties.authorizationFlow
                )

                else -> template?.authorizationFlow
            }
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val allowedRedirectUris = if (allowedGrantTypes?.contains(GrantType.AUTHORIZATION_CODE) != false) {
            try {
                when {
                    properties.allowedRedirectUris != null -> fieldParser.getAllowedRedirectUrisOrNull(
                        configKey = "$configKeyPrefix.allowed-redirect-uris",
                        uris = properties.uris,
                        allowedRedirectUris = properties.allowedRedirectUris,
                        errors = clientErrors
                    )

                    template?.allowedRedirectUris != null -> template.allowedRedirectUris
                    else -> {
                        clientErrors.add(
                            configExceptionOf(
                                "$configKeyPrefix.allowed-redirect-uris",
                                "config.client.allowed_redirect_uris.missing"
                            )
                        )
                        null
                    }
                }
            } catch (e: ConfigurationException) {
                clientErrors.add(e)
                null
            }
        } else {
            if (!properties.allowedRedirectUris.isNullOrEmpty()) {
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
            when {
                properties.allowedScopes != null -> fieldParser.getScopes(
                    key = "$configKeyPrefix.allowed-scopes",
                    scopes = properties.allowedScopes,
                    errors = clientErrors
                )?.toSet()

                else -> template?.allowedScopes
            }
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val defaultScopes = try {
            when {
                properties.defaultScopes != null -> fieldParser.getScopes(
                    key = "$configKeyPrefix.default-scopes",
                    scopes = properties.defaultScopes,
                    errors = clientErrors
                )

                else -> template?.defaultScopes
            }
        } catch (e: ConfigurationException) {
            clientErrors.add(e)
            null
        }

        val authorizationWebhook = try {
            when {
                properties.authorizationWebhook != null -> fieldParser.getAuthorizationWebhook(
                    configKey = "$configKeyPrefix.authorization-webhook",
                    webhookConfig = properties.authorizationWebhook,
                    errors = clientErrors
                )

                else -> template?.authorizationWebhook
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
