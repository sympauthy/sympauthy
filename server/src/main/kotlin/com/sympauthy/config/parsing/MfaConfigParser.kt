package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.MfaConfigurationProperties
import com.sympauthy.config.properties.MfaConfigurationProperties.Companion.MFA_KEY
import com.sympauthy.config.properties.MfaTotpConfigurationProperties
import com.sympauthy.config.properties.MfaTotpConfigurationProperties.Companion.MFA_TOTP_KEY
import jakarta.inject.Singleton

data class ParsedMfaConfig(
    val required: Boolean?,
    val totpEnabled: Boolean?
)

@Singleton
class MfaConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        properties: MfaConfigurationProperties,
        totpProperties: MfaTotpConfigurationProperties
    ): ParsedMfaConfig {
        val required = ctx.parse {
            parser.getBooleanOrThrow(properties, "$MFA_KEY.required", MfaConfigurationProperties::required)
        }
        val totpEnabled = ctx.parse {
            parser.getBooleanOrThrow(totpProperties, "$MFA_TOTP_KEY.enabled", MfaTotpConfigurationProperties::enabled)
        }
        return ParsedMfaConfig(required = required, totpEnabled = totpEnabled)
    }
}
