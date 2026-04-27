package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.factory.ClaimAclFactory
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.parsing.ParsedClaim
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.DEFAULT
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClaimsConfigValidator(
    @Inject private val claimAclFactory: ClaimAclFactory
) {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedClaim>,
        templates: Map<String, ClaimTemplate>,
        audiencesById: Map<String, Audience>,
        identifierClaims: List<String>?,
        userMergingEnabled: Boolean?
    ): List<Claim> {
        val claims = parsed.mapNotNull { parsedClaim ->
            validateClaim(ctx, parsedClaim, templates, audiencesById)
        }

        // Validate identifier claims are enabled.
        if (userMergingEnabled == true) {
            val enabledClaimIds = claims.filter { it.enabled }.map { it.id }.toSet()
            identifierClaims?.forEach { identifierClaimId ->
                if (identifierClaimId !in enabledClaimIds) {
                    ctx.addError(
                        configExceptionOf(
                            "$CLAIMS_KEY.$identifierClaimId",
                            "config.auth.identifier_claim.disabled",
                            "claim" to identifierClaimId
                        )
                    )
                }
            }
        }

        return claims
    }

    private fun validateClaim(
        ctx: ConfigParsingContext,
        parsed: ParsedClaim,
        templates: Map<String, ClaimTemplate>,
        audiencesById: Map<String, Audience>
    ): Claim? {
        val configKeyPrefix = "$CLAIMS_KEY.${parsed.id}"

        // Validate audience cross-reference.
        val audienceId = validateAudienceId(
            ctx, parsed.audienceId, audiencesById,
            "$configKeyPrefix.audience", "config.claim.audience.not_found"
        )

        // Build ACL via ClaimAclFactory (will be properly split in a later step).
        val template = templates.values.find { t ->
            // Find the template that was resolved for this claim during parsing.
            // For generated claims, use "default"; for others, match by audienceId pattern.
            true
        }

        val errors = mutableListOf<ConfigurationException>()
        val acl = if (parsed.generated) {
            val generatedClaim = GeneratedOpenIdConnectClaim.entries
                .first { it.id == parsed.id }
            claimAclFactory.buildGeneratedClaimAcl(
                acl = parsed.acl,
                template = templates[DEFAULT],
                configKeyPrefix = configKeyPrefix,
                consentScope = generatedClaim.scope,
                errors = errors
            )
        } else {
            claimAclFactory.buildAcl(
                acl = parsed.acl,
                template = templates[DEFAULT],
                configKeyPrefix = configKeyPrefix,
                defaultConsentScope = null,
                errors = errors
            )
        }
        errors.forEach { ctx.addError(it) }

        if (parsed.dataType == null) return null

        return Claim(
            id = parsed.id,
            enabled = parsed.enabled,
            verifiedId = parsed.verifiedId,
            dataType = parsed.dataType,
            group = parsed.group,
            required = parsed.required,
            generated = parsed.generated,
            userInputted = if (!parsed.generated) acl.consent.writableByUser else false,
            allowedValues = parsed.allowedValues,
            audienceId = audienceId,
            acl = acl
        )
    }
}
