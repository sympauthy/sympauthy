package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.ClaimTemplatesConfig
import com.sympauthy.config.model.DisabledClaimTemplatesConfig
import com.sympauthy.config.model.EnabledClaimTemplatesConfig
import com.sympauthy.config.parsing.ClaimTemplatesConfigParser
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties
import com.sympauthy.config.validation.ClaimTemplatesConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ClaimTemplatesConfigFactory(
    @Inject private val claimTemplatesParser: ClaimTemplatesConfigParser,
    @Inject private val claimTemplatesValidator: ClaimTemplatesConfigValidator
) {

    @Singleton
    fun provideClaimTemplates(
        templatesList: List<ClaimTemplateConfigurationProperties>
    ): ClaimTemplatesConfig {
        val ctx = ConfigParsingContext()
        val parsed = claimTemplatesParser.parse(ctx, templatesList)
        val templates = claimTemplatesValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledClaimTemplatesConfig(ctx.errors)
        else EnabledClaimTemplatesConfig(templates)
    }
}
