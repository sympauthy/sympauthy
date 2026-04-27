package com.sympauthy.config.parsing

import com.sympauthy.business.model.client.GrantType
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClientTemplate
import com.sympauthy.config.properties.ClientConfigurationProperties
import com.sympauthy.config.properties.ClientConfigurationProperties.Companion.CLIENTS_KEY
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.DEFAULT
import jakarta.inject.Inject
import jakarta.inject.Singleton

data class ParsedClient(
    val id: String,
    val audienceId: String?,
    val isPublic: Boolean,
    val secret: String?,
    val template: ClientTemplate?,
    val allowedGrantTypes: Set<GrantType>?,
    val authorizationFlowId: String?,
    val allowedRedirectUris: List<String>?,
    /** Whether [allowedRedirectUris] was explicitly set on the client (vs inherited from template). */
    val hasExplicitRedirectUris: Boolean,
    val allowedScopes: List<String>?,
    val defaultScopes: List<String>?,
    val authorizationWebhook: ParsedAuthorizationWebhook?
)

@Singleton
class ClientsConfigParser(
    @Inject private val parser: ConfigParser,
    @Inject private val fieldParser: ClientConfigFieldParser
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

        val allowedGrantTypes = if (properties.allowedGrantTypes != null) {
            fieldParser.parseGrantTypes(ctx, "$configKeyPrefix.allowed-grant-types", properties.allowedGrantTypes)
        } else {
            template?.allowedGrantTypes
        }

        val allowedRedirectUris = if (properties.allowedRedirectUris != null) {
            fieldParser.parseRedirectUris(
                ctx, "$configKeyPrefix.allowed-redirect-uris", properties.uris, properties.allowedRedirectUris
            )
        } else {
            template?.allowedRedirectUris
        }

        val authorizationWebhook = if (properties.authorizationWebhook != null) {
            fieldParser.parseWebhook(ctx, "$configKeyPrefix.authorization-webhook", properties.authorizationWebhook)
        } else {
            template?.authorizationWebhook?.let {
                ParsedAuthorizationWebhook(url = it.url, secret = it.secret, onFailure = it.onFailure)
            }
        }

        return ParsedClient(
            id = properties.id,
            audienceId = audienceId,
            isPublic = isPublic,
            secret = secret,
            template = template,
            allowedGrantTypes = allowedGrantTypes,
            authorizationFlowId = properties.authorizationFlow,
            allowedRedirectUris = allowedRedirectUris,
            hasExplicitRedirectUris = properties.allowedRedirectUris != null,
            allowedScopes = properties.allowedScopes,
            defaultScopes = properties.defaultScopes,
            authorizationWebhook = authorizationWebhook
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
