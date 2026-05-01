package com.sympauthy.config.factory

import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.model.*
import com.sympauthy.config.properties.BootstrapInvitationConfigurationProperties
import com.sympauthy.config.validation.BootstrapInvitationsConfigValidator
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Factory
class BootstrapInvitationsConfigFactory(
    @Inject private val validator: BootstrapInvitationsConfigValidator,
    @Inject private val uncheckedClaimsConfig: ClaimsConfig
) {

    @Singleton
    fun provideBootstrapInvitations(
        propertiesList: List<BootstrapInvitationConfigurationProperties>
    ): BootstrapInvitationsConfig {
        if (propertiesList.isEmpty()) {
            return EnabledBootstrapInvitationsConfig(emptyList())
        }
        if (uncheckedClaimsConfig is DisabledClaimsConfig) {
            return DisabledBootstrapInvitationsConfig(emptyList())
        }
        val enabledClaims = (uncheckedClaimsConfig as EnabledClaimsConfig)
            .claims
            .filter { it.enabled }

        val ctx = ConfigParsingContext()
        val invitations = validator.validate(ctx, propertiesList, enabledClaims)
        return if (ctx.hasErrors) DisabledBootstrapInvitationsConfig(ctx.errors)
        else EnabledBootstrapInvitationsConfig(invitations)
    }
}