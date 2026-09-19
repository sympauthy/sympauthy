package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.parsing.ParsedClaim
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClaimsConfigValidator(
    @Inject private val claimAclValidator: ClaimAclValidator
) {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedClaim>,
        templates: Map<String, ClaimTemplate>,
        audiencesById: Map<String, Audience>,
        scopesById: Map<String, Scope>,
        identifierClaims: List<String>?,
        userMergingEnabled: Boolean?
    ): List<Claim> {
        val claims = parsed.mapNotNull { parsedClaim ->
            validateClaim(ctx, parsedClaim, audiencesById, scopesById)
        }

        // An identifier claim belongs to no audience, and saying otherwise is refused rather than ignored.
        // auth.identifier-claims is declared once for the deployment, so every audience signs people in with
        // the same claim — and a restricted one would be filtered out of the very reads that resolve an
        // account, leaving a deployment whose sign-in silently stops seeing the value it identifies people by.
        identifierClaims?.forEach { identifierClaimId ->
            val identifierClaim = claims.firstOrNull { it.id == identifierClaimId }
            if (identifierClaim?.audienceId != null) {
                ctx.addError(
                    configExceptionOf(
                        "$CLAIMS_KEY.$identifierClaimId.audience",
                        "config.claims.identifier_claim.audience_restricted",
                        "claim" to identifierClaimId,
                        "audience" to identifierClaim.audienceId
                    )
                )
            }
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
        audiencesById: Map<String, Audience>,
        scopesById: Map<String, Scope>
    ): Claim? {
        val configKeyPrefix = "$CLAIMS_KEY.${parsed.id}"

        ctx.refuseReservedIdentifier(configKeyPrefix, parsed.id)

        // Validate audience cross-reference.
        val audienceId = validateAudienceId(
            ctx, parsed.audienceId, audiencesById,
            "$configKeyPrefix.audience", "config.claim.audience.not_found"
        )

        // Validate ACL scope references.
        val acl = if (parsed.generated) {
            val generatedClaim = GeneratedOpenIdConnectClaim.entries.first { it.id == parsed.id }
            claimAclValidator.validateGeneratedClaimAcl(ctx, parsed.acl, configKeyPrefix, generatedClaim.scope)
        } else {
            claimAclValidator.validateAcl(ctx, parsed.acl, configKeyPrefix, scopesById)
        }

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
