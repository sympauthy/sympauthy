package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.parsing.ParsedClaim
import com.sympauthy.config.parsing.normalizeClaimId
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
        writtenKeys: Set<String>,
        templates: Map<String, ClaimTemplate>,
        audiencesById: Map<String, Audience>,
        scopesById: Map<String, Scope>,
        identifierClaims: List<String>?,
        userMergingEnabled: Boolean?
    ): List<Claim> {
        refuseKeysWrittenOnAGeneratedClaim(ctx, writtenKeys)

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

    /**
     * Record an error against every one of the [writtenKeys] a deployment wrote under a generated
     * claim, so that a setting which will not take effect is never accepted in silence. A generated
     * claim reads none of them, whichever key it is.
     *
     * The key is refused rather than the value under it, which is what makes this answer for the whole
     * section rather than for a list of properties. A key written with nothing under it is refused,
     * because a deployment that wrote one believes it is deciding something; a value equal to the one
     * the server would have used is refused too, because a rule firing only where the two differ is
     * one nobody can predict from their own file; and a property added to a claim tomorrow is refused
     * without anyone having to say so.
     *
     * The error names the key as the file spells it, so an operator reads back what they wrote — the
     * claim id included, which Micronaut would otherwise hand over hyphenated.
     */
    private fun refuseKeysWrittenOnAGeneratedClaim(ctx: ConfigParsingContext, writtenKeys: Set<String>) {
        writtenKeys.sorted().forEach { key ->
            val claimId = key.removePrefix("$CLAIMS_KEY.").substringBefore('.', "").normalizeClaimId()
            if (claimId !in GeneratedOpenIdConnectClaim.ids) return@forEach
            ctx.addError(
                configExceptionOf(key, "config.claim.generated.not_configurable", "claim" to claimId)
            )
        }
    }

    /**
     * Record an error where a claim is advertised in the discovery document and carried by no place at all.
     *
     * `claims_supported` says what a client could be told, so a name in it a client can never obtain a
     * value for is the one direction publication may not go: a deployment stays free to carry what it does
     * not advertise, and this is what stops it advertising what it does not carry.
     *
     * It is checked on the resolved set rather than on the claim's own entry, so that a template naming
     * [ClaimPublicationPlace.DISCOVERY] and a claim naming a place that carries the value agree — which is what
     * makes the value settable on a template at all. The error therefore names the claim's own key, which
     * is where an operator writes the place that is missing.
     */
    private fun refuseAdvertisedWithoutACarrier(
        ctx: ConfigParsingContext,
        parsed: ParsedClaim,
        configKeyPrefix: String
    ) {
        if (ClaimPublicationPlace.DISCOVERY !in parsed.publishedIn) return
        if (parsed.publishedIn.any(ClaimPublicationPlace::carriesAValue)) return
        ctx.addError(
            configExceptionOf(
                "$configKeyPrefix.published-in",
                "config.claim.published_in.advertised_without_a_carrier",
                "claim" to parsed.id
            )
        )
    }

    private fun validateClaim(
        ctx: ConfigParsingContext,
        parsed: ParsedClaim,
        audiencesById: Map<String, Audience>,
        scopesById: Map<String, Scope>
    ): Claim? {
        val configKeyPrefix = "$CLAIMS_KEY.${parsed.id}"

        ctx.refuseReservedIdentifier(configKeyPrefix, parsed.id)

        refuseAdvertisedWithoutACarrier(ctx, parsed, configKeyPrefix)

        // Validate audience cross-reference.
        val audienceId = validateAudienceId(
            ctx, parsed.audienceId, audiencesById,
            "$configKeyPrefix.audience", "config.claim.audience.not_found"
        )

        // Validate ACL scope references.
        val acl = if (parsed.generated) {
            val generatedClaim = GeneratedOpenIdConnectClaim.entries.first { it.id == parsed.id }
            generatedClaim.acl
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
            collectedInFlow = if (!parsed.generated) acl.consent.collectedInFlow else false,
            allowedValues = parsed.allowedValues,
            audienceId = audienceId,
            publishedIn = parsed.publishedIn,
            acl = acl
        )
    }
}
