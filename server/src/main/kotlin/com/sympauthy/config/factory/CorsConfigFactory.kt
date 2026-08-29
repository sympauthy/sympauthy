package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.CorsConfig
import com.sympauthy.config.model.DisabledCorsConfig
import com.sympauthy.config.model.EnabledCorsConfig
import com.sympauthy.config.parsing.CorsConfigParser
import com.sympauthy.config.properties.CorsConfigurationProperties
import com.sympauthy.config.validation.CorsConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class CorsConfigFactory(
    @Inject private val corsParser: CorsConfigParser,
    @Inject private val corsValidator: CorsConfigValidator
) {

    @Singleton
    fun provideCorsConfig(
        properties: CorsConfigurationProperties
    ): CorsConfig {
        val ctx = ConfigParsingContext()
        val parsed = corsParser.parse(ctx, properties)
        corsValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledCorsConfig(ctx.errors)
        else EnabledCorsConfig(allowedHeaders = parsed.allowedHeaders)
    }
}
