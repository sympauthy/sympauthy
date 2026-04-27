package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ScopeTemplate
import com.sympauthy.config.parsing.ParsedScopeTemplate
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.TEMPLATES_SCOPES_KEY
import jakarta.inject.Singleton

@Singleton
class ScopeTemplatesConfigValidator {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedScopeTemplate>
    ): Map<String, ScopeTemplate> {
        val templates = parsed.mapNotNull { template ->
            validateTemplate(ctx, template)
        }
        return templates.associateBy { it.id }
    }

    private fun validateTemplate(
        ctx: ConfigParsingContext,
        parsed: ParsedScopeTemplate
    ): ScopeTemplate? {
        val validTypes = setOf("consentable", "grantable", "client")
        if (parsed.type != null && parsed.type !in validTypes) {
            ctx.addError(
                configExceptionOf(
                    "$TEMPLATES_SCOPES_KEY.${parsed.id}.type",
                    "config.scope.invalid_type",
                    "scope" to parsed.id,
                    "type" to parsed.type
                )
            )
            return null
        }
        return ScopeTemplate(
            id = parsed.id,
            enabled = parsed.enabled,
            type = parsed.type,
            audienceId = parsed.audienceId
        )
    }
}
