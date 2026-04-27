package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.AudiencesConfig
import com.sympauthy.config.model.DisabledAudiencesConfig
import com.sympauthy.config.model.EnabledAudiencesConfig
import com.sympauthy.config.parsing.AudiencesConfigParser
import com.sympauthy.config.properties.AudienceConfigurationProperties
import com.sympauthy.config.validation.AudiencesConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AudiencesConfigFactory(
    @Inject private val audiencesParser: AudiencesConfigParser,
    @Inject private val audiencesValidator: AudiencesConfigValidator
) {

    @Singleton
    fun provideAudiences(
        propertiesList: List<AudienceConfigurationProperties>
    ): AudiencesConfig {
        val ctx = ConfigParsingContext()
        val parsed = audiencesParser.parse(ctx, propertiesList)
        val audiences = audiencesValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledAudiencesConfig(ctx.errors)
        else EnabledAudiencesConfig(audiences)
    }
}
