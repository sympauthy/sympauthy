package com.sympauthy.config.factory

import com.sympauthy.business.model.securitycontext.EdgeProviderProfile
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.DisabledAdvancedConfig
import com.sympauthy.config.parsing.AdvancedConfigParser
import com.sympauthy.config.properties.AccessReviewWebhookConfigurationProperties
import com.sympauthy.config.properties.AdvancedConfigurationProperties
import com.sympauthy.config.properties.AuthorizationWebhookConfigurationProperties
import com.sympauthy.config.properties.HashConfigurationProperties
import com.sympauthy.config.properties.InvitationConfigurationProperties
import com.sympauthy.config.properties.InvitationHashConfigurationProperties
import com.sympauthy.config.properties.JwtConfigurationProperties
import com.sympauthy.config.properties.PaginationConfigurationProperties
import com.sympauthy.config.properties.SecurityContextConfigurationProperties
import com.sympauthy.config.properties.ValidationCodeConfigurationProperties
import com.sympauthy.config.validation.AdvancedConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class AdvancedConfigFactory(
    @Inject private val advancedParser: AdvancedConfigParser,
    @Inject private val advancedValidator: AdvancedConfigValidator,
    /**
     * Every extraction published for a proxy, which is the set of providers a deployment may name.
     *
     * A configuration is built out of them and none of them reads one, so nothing here can turn into
     * a cycle: an extraction taking the configuration it is selected by would not start.
     */
    @Inject private val profiles: List<EdgeProviderProfile>
) {

    @Singleton
    fun provideConfig(
        properties: AdvancedConfigurationProperties,
        jwtProperties: JwtConfigurationProperties,
        hashProperties: HashConfigurationProperties,
        invitationProperties: InvitationConfigurationProperties,
        invitationHashProperties: InvitationHashConfigurationProperties,
        validationCodeProperties: ValidationCodeConfigurationProperties,
        authorizationWebhookProperties: AuthorizationWebhookConfigurationProperties,
        paginationProperties: PaginationConfigurationProperties,
        securityContextProperties: SecurityContextConfigurationProperties,
        accessReviewWebhookProperties: AccessReviewWebhookConfigurationProperties,
    ): AdvancedConfig {
        val ctx = ConfigParsingContext()
        val parsed = advancedParser.parse(
            ctx, properties, jwtProperties, hashProperties,
            invitationProperties, invitationHashProperties,
            validationCodeProperties, authorizationWebhookProperties, paginationProperties,
            securityContextProperties, accessReviewWebhookProperties
        )
        val config = advancedValidator.validate(ctx, parsed, profiles.associateBy(EdgeProviderProfile::name))
        return config ?: DisabledAdvancedConfig(ctx.errors)
    }
}
