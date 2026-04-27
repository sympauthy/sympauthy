package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ScopeTemplate
import com.sympauthy.config.properties.ScopeConfigurationProperties
import com.sympauthy.config.properties.ScopeConfigurationProperties.Companion.SCOPES_KEY
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.DEFAULT_CUSTOM
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.DEFAULT_OPENID
import com.sympauthy.business.model.user.isOpenIdConnectScope
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.DEFAULT_TEMPLATE_NAMES
import jakarta.inject.Singleton

data class ParsedScopeConfig(
    val id: String,
    val isOpenIdConnect: Boolean,
    val enabled: Boolean?,
    val type: String?,
    val audienceId: String?
)

@Singleton
class ScopeConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        propertiesList: List<ScopeConfigurationProperties>,
        templates: Map<String, ScopeTemplate>
    ): List<ParsedScopeConfig> {
        return propertiesList.map { properties ->
            val isOpenIdConnect = properties.id.isOpenIdConnectScope()
            val defaultTemplateName = if (isOpenIdConnect) DEFAULT_OPENID else DEFAULT_CUSTOM
            val template = resolveTemplate(ctx, properties, templates, defaultTemplateName)

            val configKeyPrefix = "$SCOPES_KEY.${properties.id}"
            val enabled = ctx.parse {
                parser.getBoolean(
                    properties, "$configKeyPrefix.enabled",
                    ScopeConfigurationProperties::enabled
                )
            } ?: template?.enabled

            val type = (properties.type ?: template?.type)?.lowercase()
            val audienceId = properties.audience ?: template?.audienceId

            ParsedScopeConfig(
                id = properties.id,
                isOpenIdConnect = isOpenIdConnect,
                enabled = enabled,
                type = type,
                audienceId = audienceId
            )
        }
    }

    private fun resolveTemplate(
        ctx: ConfigParsingContext,
        properties: ScopeConfigurationProperties,
        templates: Map<String, ScopeTemplate>,
        defaultTemplateName: String
    ): ScopeTemplate? {
        val templateName = properties.template
        if (templateName != null) {
            if (templateName in DEFAULT_TEMPLATE_NAMES) {
                ctx.addError(
                    configExceptionOf(
                        "$SCOPES_KEY.${properties.id}.template",
                        "config.scope.template.cannot_reference_default",
                        "defaultTemplates" to DEFAULT_TEMPLATE_NAMES.joinToString(", ")
                    )
                )
                return null
            }
            val template = templates[templateName]
            if (template == null) {
                ctx.addError(
                    configExceptionOf(
                        "$SCOPES_KEY.${properties.id}.template",
                        "config.scope.template.not_found",
                        "template" to templateName,
                        "scope" to properties.id,
                        "availableTemplates" to templates.keys.filter { it !in DEFAULT_TEMPLATE_NAMES }
                            .joinToString(", ")
                    )
                )
                return null
            }
            return template
        }
        return templates[defaultTemplateName]
    }
}
