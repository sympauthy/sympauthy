package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClientTemplate
import com.sympauthy.config.properties.ClientConfigurationProperties
import com.sympauthy.config.properties.ClientConfigurationProperties.AuthorizationWebhookConfig
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.DEFAULT
import jakarta.inject.Singleton

data class ParsedClient(
    val id: String,
    val audienceId: String?,
    val isPublic: Boolean,
    val secret: String?,
    val template: ClientTemplate?,
    val allowedGrantTypes: List<String>?,
    val authorizationFlowId: String?,
    val uris: Map<String, String>?,
    val allowedRedirectUris: List<String>?,
    val allowedScopes: List<String>?,
    val defaultScopes: List<String>?,
    val authorizationWebhook: AuthorizationWebhookConfig?
)

@Singleton
class ClientsConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        propertiesList: List<ClientConfigurationProperties>,
        templates: Map<String, ClientTemplate>
    ): List<ParsedClient> {
        return propertiesList.map { properties ->
            parseClient(ctx, properties, templates)
        }
    }

    private fun parseClient(
        ctx: ConfigParsingContext,
        properties: ClientConfigurationProperties,
        templates: Map<String, ClientTemplate>
    ): ParsedClient {
        val template = resolveTemplate(ctx, properties, templates)
        val configKeyPrefix = "$CLIENTS_KEY.${properties.id}"

        val isPublic = ctx.parse {
            parser.getBoolean(properties, "$configKeyPrefix.public", ClientConfigurationProperties::`public`)
        } ?: template?.public ?: false

        val secret = ctx.parse {
            parser.getString(properties, "$configKeyPrefix.secret", ClientConfigurationProperties::secret)
        }

        val audienceId = properties.audience ?: template?.audienceId

        return ParsedClient(
            id = properties.id,
            audienceId = audienceId,
            isPublic = isPublic,
            secret = secret,
            template = template,
            allowedGrantTypes = properties.allowedGrantTypes,
            authorizationFlowId = properties.authorizationFlow,
            uris = properties.uris,
            allowedRedirectUris = properties.allowedRedirectUris,
            allowedScopes = properties.allowedScopes,
            defaultScopes = properties.defaultScopes,
            authorizationWebhook = properties.authorizationWebhook
        )
    }

    private fun resolveTemplate(
        ctx: ConfigParsingContext,
        properties: ClientConfigurationProperties,
        templates: Map<String, ClientTemplate>
    ): ClientTemplate? {
        val templateName = properties.template
        if (templateName != null) {
            if (templateName == DEFAULT) {
                ctx.addError(
                    configExceptionOf(
                        "$CLIENTS_KEY.${properties.id}.template",
                        "config.client.template.cannot_reference_default"
                    )
                )
                return null
            }
            val template = templates[templateName]
            if (template == null) {
                ctx.addError(
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
}
