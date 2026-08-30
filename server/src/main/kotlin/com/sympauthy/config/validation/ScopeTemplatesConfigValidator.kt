package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ScopeTemplate
import com.sympauthy.config.parsing.ParsedScopeTemplate
import com.sympauthy.config.properties.ScopeTemplateConfigurationProperties.Companion.DEFAULT_OPENID
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
        if (parsed.id == DEFAULT_OPENID && refuseOpenIdConnectDefaults(ctx, parsed)) {
            return null
        }
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

    /**
     * Record an error for every setting the template applied to the OpenID Connect scopes carries,
     * and answer whether it carried any.
     *
     * The specification names those scopes and decides what they are, so there is nothing here for
     * a default to say: an audience and a type cannot apply to them at all, and a deployment that
     * no longer wants one turns that scope off by name rather than turning the set off at once.
     * Every setting is reported, rather than the first, so that one startup names every line to
     * delete.
     */
    private fun refuseOpenIdConnectDefaults(
        ctx: ConfigParsingContext,
        parsed: ParsedScopeTemplate
    ): Boolean {
        val refusals = listOfNotNull(
            parsed.enabled?.let { "enabled" to "config.scope.template.enabled_not_allowed_on_default_openid" },
            parsed.type?.let { "type" to "config.scope.template.type_not_allowed_on_default_openid" },
            parsed.audienceId?.let { "audience" to "config.scope.template.audience_not_allowed_on_default_openid" }
        )
        refusals.forEach { (setting, messageId) ->
            ctx.addError(
                configExceptionOf(
                    "$TEMPLATES_SCOPES_KEY.${parsed.id}.$setting",
                    messageId,
                    "template" to parsed.id
                )
            )
        }
        return refusals.isNotEmpty()
    }
}
