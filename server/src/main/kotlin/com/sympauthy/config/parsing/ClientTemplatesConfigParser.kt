package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.ClientConfigurationProperties.AuthorizationWebhookConfig
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.TEMPLATES_CLIENTS_KEY
import jakarta.inject.Singleton

data class ParsedClientTemplate(
    val id: String,
    val audienceId: String?,
    val isPublic: Boolean?,
    val allowedGrantTypes: List<String>?,
    val authorizationFlowId: String?,
    val uris: Map<String, String>?,
    val allowedRedirectUris: List<String>?,
    val allowedScopes: List<String>?,
    val defaultScopes: List<String>?,
    val authorizationWebhook: AuthorizationWebhookConfig?
)

@Singleton
class ClientTemplatesConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        templatesList: List<ClientTemplateConfigurationProperties>
    ): List<ParsedClientTemplate> {
        return templatesList.map { properties ->
            parseTemplate(ctx, properties)
        }
    }

    private fun parseTemplate(
        ctx: ConfigParsingContext,
        properties: ClientTemplateConfigurationProperties
    ): ParsedClientTemplate {
        val configKeyPrefix = "$TEMPLATES_CLIENTS_KEY.${properties.id}"

        val audienceId = ctx.parse {
            parser.getString(properties, "$configKeyPrefix.audience", ClientTemplateConfigurationProperties::audience)
        }

        val isPublic = ctx.parse {
            parser.getBoolean(properties, "$configKeyPrefix.public", ClientTemplateConfigurationProperties::`public`)
        }

        return ParsedClientTemplate(
            id = properties.id,
            audienceId = audienceId,
            isPublic = isPublic,
            allowedGrantTypes = properties.allowedGrantTypes,
            authorizationFlowId = properties.authorizationFlow,
            uris = properties.uris,
            allowedRedirectUris = properties.allowedRedirectUris,
            allowedScopes = properties.allowedScopes,
            defaultScopes = properties.defaultScopes,
            authorizationWebhook = properties.authorizationWebhook
        )
    }
}
