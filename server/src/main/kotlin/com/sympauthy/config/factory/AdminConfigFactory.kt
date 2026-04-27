package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.AdminConfig
import com.sympauthy.config.model.DisabledAdminConfig
import com.sympauthy.config.model.EnabledAdminConfig
import com.sympauthy.config.parsing.AdminConfigParser
import com.sympauthy.config.properties.AdminConfigurationProperties
import com.sympauthy.config.validation.AdminConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AdminConfigFactory(
    @Inject private val adminParser: AdminConfigParser,
    @Inject private val adminValidator: AdminConfigValidator
) {

    @Singleton
    fun provideAdminConfig(
        properties: AdminConfigurationProperties
    ): AdminConfig {
        val ctx = ConfigParsingContext()
        val parsed = adminParser.parse(ctx, properties)
        adminValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledAdminConfig(ctx.errors)
        else EnabledAdminConfig(enabled = parsed.enabled!!, integratedUi = parsed.integratedUi!!)
    }
}
