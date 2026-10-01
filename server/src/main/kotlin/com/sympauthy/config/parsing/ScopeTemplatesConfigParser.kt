package com.sympauthy.config.parsing

import com.sympauthy.business.model.oauth2.ScopePublicationPlace
import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.TEMPLATES_SCOPES_KEY
import jakarta.inject.Singleton

data class ParsedScopeTemplate(
    val id: String,
    val enabled: Boolean?,
    val publishedIn: Set<ScopePublicationPlace>?,
    val type: String?,
    val audienceId: String?
)

@Singleton
class ScopeTemplatesConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        templatesList: List<ScopeTemplateConfigurationProperties>
    ): List<ParsedScopeTemplate> {
        return templatesList.map { properties ->
            parseTemplate(ctx, properties)
        }
    }

    private fun parseTemplate(
        ctx: ConfigParsingContext,
        properties: ScopeTemplateConfigurationProperties
    ): ParsedScopeTemplate {
        val configKeyPrefix = "$TEMPLATES_SCOPES_KEY.${properties.id}"
        val enabled = ctx.parse {
            parser.getBoolean(properties, "$configKeyPrefix.enabled", ScopeTemplateConfigurationProperties::enabled)
        }
        val publishedIn = parseScopePublishedIn(
            ctx, parser, properties.publishedIn, "$configKeyPrefix.published-in"
        )
        return ParsedScopeTemplate(
            id = properties.id,
            enabled = enabled,
            publishedIn = publishedIn,
            type = properties.type?.lowercase(),
            audienceId = properties.audience
        )
    }
}
