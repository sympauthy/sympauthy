package com.sympauthy.config.validation

import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.parsing.ParsedClaimTemplate
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClaimTemplatesConfigValidator(
    @Inject private val claimAclValidator: ClaimAclValidator
) {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedClaimTemplate>,
        scopesById: Map<String, EnabledScope>
    ): Map<String, ClaimTemplate> {
        val templates = parsed.mapNotNull { template ->
            validateTemplate(ctx, template, scopesById)
        }
        return templates.associateBy { it.id }
    }

    private fun validateTemplate(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimTemplate,
        scopesById: Map<String, EnabledScope>
    ): ClaimTemplate? {
        val configKeyPrefix = "$TEMPLATES_CLAIMS_KEY.${parsed.id}"
        val subCtx = ctx.child()

        val acl = claimAclValidator.validateTemplateAcl(subCtx, parsed.acl, configKeyPrefix, scopesById)

        ctx.merge(subCtx)
        if (subCtx.hasErrors) return null

        return ClaimTemplate(
            id = parsed.id,
            enabled = parsed.enabled,
            required = parsed.required,
            group = parsed.group,
            audienceId = parsed.audienceId,
            allowedValues = parsed.allowedValues,
            acl = acl
        )
    }
}
