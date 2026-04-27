package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.DisabledUIConfig
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.UIConfig
import com.sympauthy.config.parsing.UIConfigParser
import com.sympauthy.config.properties.UIConfigurationProperties
import com.sympauthy.config.validation.UIConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class UIConfigFactory(
    @Inject private val uiParser: UIConfigParser,
    @Inject private val uiValidator: UIConfigValidator
) {

    @Singleton
    fun provideUIConfig(
        properties: UIConfigurationProperties
    ): UIConfig {
        val ctx = ConfigParsingContext()
        val parsed = uiParser.parse(ctx, properties)
        uiValidator.validate(ctx, parsed)
        return if (ctx.hasErrors) DisabledUIConfig(ctx.errors)
        else EnabledUIConfig(displayName = parsed.displayName!!)
    }
}
