package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.AdminConfigurationProperties
import com.sympauthy.config.properties.AdminConfigurationProperties.Companion.ADMIN_KEY
import jakarta.inject.Singleton

data class ParsedAdminConfig(
    val enabled: Boolean?,
    val integratedUi: Boolean?
)

@Singleton
class AdminConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        properties: AdminConfigurationProperties
    ): ParsedAdminConfig {
        val enabled = ctx.parse {
            parser.getBooleanOrThrow(properties, "$ADMIN_KEY.enabled", AdminConfigurationProperties::enabled)
        }
        val integratedUi = ctx.parse {
            parser.getBooleanOrThrow(properties, "$ADMIN_KEY.integrated-ui", AdminConfigurationProperties::integratedUi)
        }
        return ParsedAdminConfig(enabled = enabled, integratedUi = integratedUi)
    }
}
