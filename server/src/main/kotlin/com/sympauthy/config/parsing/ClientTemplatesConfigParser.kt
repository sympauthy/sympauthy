package com.sympauthy.config.parsing

import com.sympauthy.business.model.client.GrantType
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties
import com.sympauthy.config.properties.ClientTemplateConfigurationProperties.Companion.TEMPLATES_CLIENTS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI

data class ParsedClientTemplate(
    val id: String,
    val audienceId: String?,
    val isPublic: Boolean?,
    val allowedGrantTypes: Set<GrantType>?,
    val authorizationFlowId: String?,
    val allowedRedirectUris: List<String>?,
    val allowedScopes: List<String>?,
    val defaultScopes: List<String>?,
    val authorizationWebhook: ParsedAuthorizationWebhook?
)

@Singleton
class ClientTemplatesConfigParser(
    @Inject private val parser: ConfigParser,
    @Inject private val fieldParser: ClientConfigFieldParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        templatesList: List<ClientTemplateConfigurationProperties>,
        rootUri: URI
    ): List<ParsedClientTemplate> {
        return templatesList.map { properties ->
            parseTemplate(ctx, properties, rootUri)
        }
    }

    private fun parseTemplate(
        ctx: ConfigParsingContext,
        properties: ClientTemplateConfigurationProperties,
        rootUri: URI
    ): ParsedClientTemplate {
        val configKeyPrefix = "$TEMPLATES_CLIENTS_KEY.${properties.id}"

        val audienceId = ctx.parse {
            parser.getString(properties, "$configKeyPrefix.audience", ClientTemplateConfigurationProperties::audience)
        }

        val isPublic = ctx.parse {
            parser.getBoolean(properties, "$configKeyPrefix.public", ClientTemplateConfigurationProperties::`public`)
        }

        val allowedGrantTypes = fieldParser.parseGrantTypes(
            ctx, "$configKeyPrefix.allowed-grant-types", properties.allowedGrantTypes
        )

        val allowedRedirectUris = fieldParser.parseRedirectUris(
            ctx, "$configKeyPrefix.allowed-redirect-uris", properties.uris, properties.allowedRedirectUris,
            rootUri
        )

        val authorizationWebhook = fieldParser.parseWebhook(
            ctx, "$configKeyPrefix.authorization-webhook", properties.authorizationWebhook, null
        )

        return ParsedClientTemplate(
            id = properties.id,
            audienceId = audienceId,
            isPublic = isPublic,
            allowedGrantTypes = allowedGrantTypes,
            authorizationFlowId = properties.authorizationFlow,
            allowedRedirectUris = allowedRedirectUris,
            allowedScopes = properties.allowedScopes,
            defaultScopes = properties.defaultScopes,
            authorizationWebhook = authorizationWebhook
        )
    }
}
