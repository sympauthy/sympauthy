package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.BootstrapInvitation
import com.sympauthy.config.properties.BootstrapInvitationConfigurationProperties
import com.sympauthy.config.properties.BootstrapInvitationConfigurationProperties.Companion.INVITATIONS_KEY
import jakarta.inject.Singleton

@Singleton
class BootstrapInvitationsConfigValidator {

    fun validate(
        ctx: ConfigParsingContext,
        propertiesList: List<BootstrapInvitationConfigurationProperties>,
        audiencesById: Map<String, Audience>,
        enabledClaims: List<Claim>
    ): List<BootstrapInvitation> {
        return propertiesList.mapNotNull { properties ->
            validateInvitation(ctx, properties, audiencesById, enabledClaims)
        }
    }

    private fun validateInvitation(
        ctx: ConfigParsingContext,
        properties: BootstrapInvitationConfigurationProperties,
        audiencesById: Map<String, Audience>,
        enabledClaims: List<Claim>
    ): BootstrapInvitation? {
        val subCtx = ctx.child()
        val configKeyPrefix = "$INVITATIONS_KEY.${properties.id}"

        val audienceId = properties.audience
        if (audienceId.isNullOrBlank()) {
            subCtx.addError(
                configExceptionOf(
                    "$configKeyPrefix.audience",
                    "config.missing"
                )
            )
        } else {
            validateAudienceId(
                subCtx, audienceId, audiencesById,
                "$configKeyPrefix.audience",
                "config.bootstrap_invitation.audience.not_found"
            )
        }

        val resolvedClaims = resolveClaimKeys(subCtx, configKeyPrefix, properties.claims, enabledClaims, audienceId)

        ctx.merge(subCtx)
        if (subCtx.hasErrors || audienceId == null) return null

        return BootstrapInvitation(
            id = properties.id,
            audienceId = audienceId,
            urlTemplate = properties.urlTemplate,
            claims = resolvedClaims,
            note = properties.note
        )
    }

    /**
     * Resolve claim keys from the config map against the actual claim definitions, and refuse a claim
     * [audienceId] does not have.
     *
     * Micronaut normalizes map keys in configuration properties (e.g. `is_sympauthy_admin` becomes
     * `is-sympauthy-admin`), but claim IDs from `@EachProperty("claims")` preserve the raw YAML key.
     * This method matches normalized keys to canonical claim IDs by comparing their normalized forms.
     *
     * The audience is the restriction [com.sympauthy.business.manager.invitation.InvitationManager] holds
     * every invitation to, asked here so that it is a startup error beside the others: left to the manager it
     * would throw inside the listener creating these at service-ready, once the deployment had already
     * reported itself healthy.
     *
     * [audienceId] is absent only when the invitation named none, which is an error of its own; the claims are
     * resolved regardless, so one startup tells the operator about both.
     */
    private fun resolveClaimKeys(
        ctx: ConfigParsingContext,
        configKeyPrefix: String,
        configClaims: Map<String, String>?,
        enabledClaims: List<Claim>,
        audienceId: String?
    ): Map<String, String>? {
        if (configClaims.isNullOrEmpty()) return configClaims
        val resolved = mutableMapOf<String, String>()
        for ((configKey, value) in configClaims) {
            val normalizedConfigKey = normalize(configKey)
            val matchingClaim = enabledClaims.firstOrNull { normalize(it.id) == normalizedConfigKey }
            if (matchingClaim == null) {
                ctx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.claims.$configKey",
                        "config.bootstrap_invitation.unknown_claim",
                        "claim" to configKey
                    )
                )
            } else if (!audienceId.isNullOrBlank() && !matchingClaim.belongsToAudience(audienceId)) {
                ctx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.claims.$configKey",
                        "config.bootstrap_invitation.claim_of_another_audience",
                        "claim" to configKey,
                        "claimAudience" to matchingClaim.audienceId.orEmpty(),
                        "audience" to audienceId
                    )
                )
            } else {
                resolved[matchingClaim.id] = value
            }
        }
        return resolved.ifEmpty { null }
    }

    private fun normalize(key: String): String = key.replace('_', '-').lowercase()
}
