package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.*
import com.sympauthy.config.parsing.ClaimsConfigParser
import com.sympauthy.config.properties.AuthConfigurationProperties
import com.sympauthy.config.properties.ClaimConfigurationProperties
import com.sympauthy.config.validation.ClaimsConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class ClaimsConfigFactory(
    @Inject private val claimsParser: ClaimsConfigParser,
    @Inject private val claimsValidator: ClaimsConfigValidator,
    @Inject private val authProperties: AuthConfigurationProperties,
    @Inject private val claimTemplatesConfig: ClaimTemplatesConfig,
    @Inject private val uncheckedAudiencesConfig: AudiencesConfig
) {

    @Singleton
    fun provideClaims(
        propertiesList: List<ClaimConfigurationProperties>
    ): ClaimsConfig {
        val enabledTemplatesConfig = claimTemplatesConfig.orNull()
            ?: return DisabledClaimsConfig(emptyList())
        val enabledAudiencesConfig = uncheckedAudiencesConfig as? EnabledAudiencesConfig
            ?: return DisabledClaimsConfig(emptyList())

        val ctx = ConfigParsingContext()
        val parsed = claimsParser.parse(ctx, propertiesList, enabledTemplatesConfig.templates)
        val claims = claimsValidator.validate(
            ctx, parsed, enabledTemplatesConfig.templates,
            enabledAudiencesConfig.audiences.associateBy { it.id },
            authProperties.identifierClaims,
            authProperties.userMergingEnabled
        )
        return if (ctx.hasErrors) DisabledClaimsConfig(ctx.errors)
        else EnabledClaimsConfig(claims)
    }
}
