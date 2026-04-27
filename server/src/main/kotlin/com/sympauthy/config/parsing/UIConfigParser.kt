package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.UIConfigurationProperties
import com.sympauthy.config.properties.UIConfigurationProperties.Companion.UI_KEY
import jakarta.inject.Singleton

data class ParsedUIConfig(
    val displayName: String?
)

@Singleton
class UIConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        properties: UIConfigurationProperties
    ): ParsedUIConfig {
        val displayName = ctx.parse {
            parser.getStringOrThrow(properties, "$UI_KEY.display-name", UIConfigurationProperties::displayName)
        }
        return ParsedUIConfig(displayName = displayName)
    }
}
