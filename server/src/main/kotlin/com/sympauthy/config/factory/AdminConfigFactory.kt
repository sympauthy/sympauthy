package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.AdminConfigParser
import com.sympauthy.config.properties.AdminConfigurationProperties
import com.sympauthy.config.validation.AdminConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AdminConfigFactory(
    @Inject private val adminParser: AdminConfigParser,
    @Inject private val adminValidator: AdminConfigValidator,
    @Inject private val uncheckedAudiencesConfig: AudiencesConfig
) {

    @Singleton
    fun provideAdminConfig(
        properties: AdminConfigurationProperties
    ): AdminConfig {
        val enabledAudiencesConfig = uncheckedAudiencesConfig as? EnabledAudiencesConfig
            ?: return DisabledAdminConfig(emptyList())
        val audiencesById = enabledAudiencesConfig.audiences.associateBy { it.id }

        val ctx = ConfigParsingContext()
        val parsed = adminParser.parse(ctx, properties)
        adminValidator.validate(ctx, parsed, audiencesById)
        return if (ctx.hasErrors) DisabledAdminConfig(ctx.errors)
        else EnabledAdminConfig(
            enabled = parsed.enabled!!,
            integratedUi = parsed.integratedUi!!,
            audienceId = parsed.audience!!
        )
    }
}
