package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.WrittenConfigurationKeys
import com.sympauthy.config.model.DisabledUIConfig
import com.sympauthy.config.model.EnabledUIConfig
import com.sympauthy.config.model.ThemeConfig
import com.sympauthy.config.model.UIConfig
import com.sympauthy.config.parsing.UIConfigParser
import com.sympauthy.config.properties.ThemeConfigurationProperties
import com.sympauthy.config.properties.ThemeConfigurationProperties.Companion.THEME_KEY
import com.sympauthy.config.properties.UIConfigurationProperties
import com.sympauthy.config.validation.UIConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class UIConfigFactory(
    @Inject private val uiParser: UIConfigParser,
    @Inject private val uiValidator: UIConfigValidator,
    @Inject private val writtenConfigurationKeys: WrittenConfigurationKeys
) {

    @Singleton
    fun provideUIConfig(
        properties: UIConfigurationProperties,
        themeProperties: ThemeConfigurationProperties
    ): UIConfig {
        val ctx = ConfigParsingContext()
        val parsed = uiParser.parse(ctx, properties, themeProperties)
        uiValidator.validate(ctx, parsed, writtenConfigurationKeys.under("$THEME_KEY."))
        return if (ctx.hasErrors) DisabledUIConfig(ctx.errors)
        else EnabledUIConfig(
            displayName = parsed.displayName,
            icon = parsed.icon,
            logo = parsed.logo,
            logoDark = parsed.logoDark,
            theme = ThemeConfig(darkMode = parsed.darkMode!!, light = parsed.light, dark = parsed.dark)
        )
    }
}
