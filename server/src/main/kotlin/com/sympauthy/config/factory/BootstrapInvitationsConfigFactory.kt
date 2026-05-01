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
    @Inject private val uncheckedAudiencesConfig: AudiencesConfig,
    @Inject private val uncheckedClaimsConfig: ClaimsConfig
) {

    @Singleton
    fun provideBootstrapInvitations(
        propertiesList: List<BootstrapInvitationConfigurationProperties>
    ): BootstrapInvitationsConfig {
        if (propertiesList.isEmpty()) {
            return EnabledBootstrapInvitationsConfig(emptyList())
        }
        if (uncheckedAudiencesConfig is DisabledAudiencesConfig) {
            return DisabledBootstrapInvitationsConfig(emptyList())
        }
        if (uncheckedClaimsConfig is DisabledClaimsConfig) {
            return DisabledBootstrapInvitationsConfig(emptyList())
        }
        val audiencesById = (uncheckedAudiencesConfig as EnabledAudiencesConfig)
            .audiences.associateBy { it.id }
        val enabledClaims = (uncheckedClaimsConfig as EnabledClaimsConfig)
            .claims
            .filter { it.enabled }

        val ctx = ConfigParsingContext()
        val invitations = validator.validate(ctx, propertiesList, audiencesById, enabledClaims)
        return if (ctx.hasErrors) DisabledBootstrapInvitationsConfig(ctx.errors)
        else EnabledBootstrapInvitationsConfig(invitations)
    }
}