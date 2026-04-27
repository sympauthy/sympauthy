package com.sympauthy.config.validation

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.factory.ClaimAclFactory
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.parsing.ParsedClaimTemplate
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClaimTemplatesConfigValidator(
    @Inject private val claimAclFactory: ClaimAclFactory
) {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedClaimTemplate>
    ): Map<String, ClaimTemplate> {
        val templates = parsed.mapNotNull { template ->
            validateTemplate(ctx, template)
        }
        return templates.associateBy { it.id }
    }

    private fun validateTemplate(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimTemplate
    ): ClaimTemplate? {
        val configKeyPrefix = "$TEMPLATES_CLAIMS_KEY.${parsed.id}"
        val subCtx = ctx.child()

        // ClaimAclFactory.buildTemplateAcl validates scope references.
        // It will be properly split into parsing/validation in a later step.
        val errors = mutableListOf<com.sympauthy.config.exception.ConfigurationException>()
        val acl = claimAclFactory.buildTemplateAcl(
            acl = parsed.acl,
            configKeyPrefix = configKeyPrefix,
            errors = errors
        )
        errors.forEach { subCtx.addError(it) }

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
