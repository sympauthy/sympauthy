package com.sympauthy.config.factory

import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplatesConfig
import com.sympauthy.config.model.DisabledClaimTemplatesConfig
import com.sympauthy.config.model.EnabledClaimTemplatesConfig
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.model.orNull
import com.sympauthy.config.parsing.ClaimTemplatesConfigParser
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties
import com.sympauthy.config.validation.ClaimTemplatesConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ClaimTemplatesConfigFactory(
    @Inject private val claimTemplatesParser: ClaimTemplatesConfigParser,
    @Inject private val claimTemplatesValidator: ClaimTemplatesConfigValidator,
    @Inject private val uncheckedScopesConfig: ScopesConfig
) {

    @Singleton
    fun provideClaimTemplates(
        templatesList: List<ClaimTemplateConfigurationProperties>
    ): ClaimTemplatesConfig {
        val enabledScopesConfig = uncheckedScopesConfig.orNull()
            ?: return DisabledClaimTemplatesConfig(emptyList())

        val ctx = ConfigParsingContext()
        val parsed = claimTemplatesParser.parse(ctx, templatesList)
        val templates = claimTemplatesValidator.validate(
            ctx, parsed, enabledScopesConfig.enabledScopes.associateBy(EnabledScope::scope)
        )
        return if (ctx.hasErrors) DisabledClaimTemplatesConfig(ctx.errors)
        else EnabledClaimTemplatesConfig(templates)
    }
}
