package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.DisabledFeaturesConfig
import com.sympauthy.config.model.EnabledFeaturesConfig
import com.sympauthy.config.model.FeaturesConfig
import com.sympauthy.config.parsing.FeaturesConfigParser
import com.sympauthy.config.properties.FeaturesConfigurationProperties
import com.sympauthy.config.validation.FeaturesConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class FeaturesConfigFactory(
    @Inject private val featuresParser: FeaturesConfigParser,
    @Inject private val featuresValidator: FeaturesConfigValidator
) {

    @Singleton
    fun providesFeature(
        propertiesList: FeaturesConfigurationProperties
    ): FeaturesConfig {
        val ctx = ConfigParsingContext()
        val parsed = featuresParser.parse(ctx, propertiesList)
        featuresValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledFeaturesConfig(ctx.errors)
        else EnabledFeaturesConfig(
            allowAccessToClientWithoutScope = parsed.allowAccessToClientWithoutScope!!,
            emailValidation = parsed.emailValidation!!,
            grantUnhandledScopes = parsed.grantUnhandledScopes!!,
            printDetailsInError = parsed.printDetailsInError!!
        )
    }
}
