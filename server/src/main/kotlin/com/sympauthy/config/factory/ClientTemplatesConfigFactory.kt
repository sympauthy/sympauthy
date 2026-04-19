package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.ClientTemplate
import com.sympauthy.config.model.ClientTemplatesConfig
import com.sympauthy.config.model.DisabledClientTemplatesConfig
import com.sympauthy.config.model.EnabledClientTemplatesConfig
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.TEMPLATES_CLIENTS_KEY
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Factory
class ClientTemplatesConfigFactory(
    @Inject private val parser: ConfigParser,
    @Inject private val fieldParser: ClientConfigFieldParser
) {

    @Singleton
    fun provideClientTemplates(
        templatesList: List<ClientTemplateConfigurationProperties>
    ): Flow<ClientTemplatesConfig> {
        return flow {
            val errors = mutableListOf<ConfigurationException>()

            val templates = templatesList.mapNotNull { properties ->
                getTemplate(properties, errors)
            }.associateBy { it.id }

            val config = if (errors.isEmpty()) {
                EnabledClientTemplatesConfig(templates)
            } else {
                DisabledClientTemplatesConfig(errors)
            }
            emit(config)
        }
    }

    private suspend fun getTemplate(
        properties: ClientTemplateConfigurationProperties,
        errors: MutableList<ConfigurationException>
    ): ClientTemplate? {
        val templateErrors = mutableListOf<ConfigurationException>()
        val configKeyPrefix = "$TEMPLATES_CLIENTS_KEY.${properties.id}"

        val isPublic = parser.getBoolean(
            properties, "$configKeyPrefix.public",
            ClientTemplateConfigurationProperties::`public`
        )

        val allowedGrantTypes = try {
            fieldParser.validateGrantTypes(
                configKey = "$configKeyPrefix.allowed-grant-types",
                allowedGrantTypes = properties.allowedGrantTypes,
                errors = templateErrors
            )
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val authorizationFlow = try {
            fieldParser.getAuthorizationFlow(
                key = "$configKeyPrefix.authorization-flow",
                flowId = properties.authorizationFlow
            )
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val allowedRedirectUris = try {
            fieldParser.validateRedirectUris(
                configKey = "$configKeyPrefix.allowed-redirect-uris",
                uris = properties.uris,
                allowedRedirectUris = properties.allowedRedirectUris,
                errors = templateErrors
            )
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val allowedScopes = try {
            fieldParser.getScopes(
                key = "$configKeyPrefix.allowed-scopes",
                scopes = properties.allowedScopes,
                errors = templateErrors
            )?.toSet()
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val defaultScopes = try {
            fieldParser.getScopes(
                key = "$configKeyPrefix.default-scopes",
                scopes = properties.defaultScopes,
                errors = templateErrors
            )
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        val authorizationWebhook = try {
            fieldParser.getAuthorizationWebhook(
                configKey = "$configKeyPrefix.authorization-webhook",
                webhookConfig = properties.authorizationWebhook,
                errors = templateErrors
            )
        } catch (e: ConfigurationException) {
            templateErrors.add(e)
            null
        }

        return if (templateErrors.isEmpty()) {
            ClientTemplate(
                id = properties.id,
                public = isPublic,
                allowedGrantTypes = allowedGrantTypes,
                authorizationFlow = authorizationFlow,
                allowedRedirectUris = allowedRedirectUris,
                allowedScopes = allowedScopes,
                defaultScopes = defaultScopes,
                authorizationWebhook = authorizationWebhook
            )
        } else {
            errors.addAll(templateErrors)
            null
        }
    }
}
