package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.ClaimsConfig
import com.sympauthy.config.model.DisabledAuthConfig
import com.sympauthy.config.parsing.AuthConfigParser
import com.sympauthy.config.properties.AuthConfigurationProperties
import com.sympauthy.config.properties.AuthorizationCodeConfigurationProperties
import com.sympauthy.config.properties.ByPasswordConfigurationProperties
import com.sympauthy.config.properties.TokenConfigurationProperties
import com.sympauthy.config.validation.AuthConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AuthConfigFactory(
    @Inject private val authParser: AuthConfigParser,
    @Inject private val authValidator: AuthConfigValidator
) {

    @Singleton
    fun provideAuthConfig(
        properties: AuthConfigurationProperties,
        tokenProperties: TokenConfigurationProperties?,
        authorizationCodeProperties: AuthorizationCodeConfigurationProperties?,
        byPasswordProperties: ByPasswordConfigurationProperties?,
        uncheckedClaimsConfig: ClaimsConfig
    ): AuthConfig {
        val ctx = ConfigParsingContext()
        val parsed = authParser.parse(
            ctx, properties, tokenProperties, authorizationCodeProperties, byPasswordProperties
        )
        val config = authValidator.validate(ctx, parsed, uncheckedClaimsConfig)
        return config ?: DisabledAuthConfig(ctx.errors)
    }
}
