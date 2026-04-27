package com.sympauthy.config.parsing

import com.sympauthy.config.ConfigParser
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.properties.FeaturesConfigurationProperties
import com.sympauthy.config.properties.FeaturesConfigurationProperties.Companion.FEATURES_KEY
import jakarta.inject.Singleton

data class ParsedFeaturesConfig(
    val allowAccessToClientWithoutScope: Boolean?,
    val emailValidation: Boolean?,
    val grantUnhandledScopes: Boolean?,
    val printDetailsInError: Boolean?
)

@Singleton
class FeaturesConfigParser(
    private val parser: ConfigParser
) {
    fun parse(
        ctx: ConfigParsingContext,
        properties: FeaturesConfigurationProperties
    ): ParsedFeaturesConfig {
        val allowAccessToClientWithoutScope = ctx.parse {
            parser.getBooleanOrThrow(
                properties, "$FEATURES_KEY.allow-access-to-client-without-scope",
                FeaturesConfigurationProperties::allowAccessToClientWithoutScope
            )
        }
        val emailValidation = ctx.parse {
            parser.getBooleanOrThrow(
                properties, "$FEATURES_KEY.email-validation",
                FeaturesConfigurationProperties::emailValidation
            )
        }
        val grantUnhandledScopes = ctx.parse {
            parser.getBooleanOrThrow(
                properties, "$FEATURES_KEY.grant-unhandled-scopes",
                FeaturesConfigurationProperties::grantUnhandledScopes
            )
        }
        val printDetailsInError = ctx.parse {
            parser.getBooleanOrThrow(
                properties, "$FEATURES_KEY.print-details-in-error",
                FeaturesConfigurationProperties::printDetailsInError
            )
        }
        return ParsedFeaturesConfig(
            allowAccessToClientWithoutScope = allowAccessToClientWithoutScope,
            emailValidation = emailValidation,
            grantUnhandledScopes = grantUnhandledScopes,
            printDetailsInError = printDetailsInError
        )
    }
}
