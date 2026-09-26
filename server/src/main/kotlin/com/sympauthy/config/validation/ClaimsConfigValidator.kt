package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.parsing.ParsedClaim
import com.sympauthy.config.parsing.normalizeClaimId
import com.sympauthy.config.properties.ClaimConfigurationProperties
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
        propertiesList: List<ClaimConfigurationProperties>,
        templates: Map<String, ClaimTemplate>,
        audiencesById: Map<String, Audience>,
        scopesById: Map<String, Scope>,
        identifierClaims: List<String>?,
        userMergingEnabled: Boolean?
    ): List<Claim> {
        refuseKeysWrittenOnAGeneratedClaim(ctx, propertiesList)

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
     * Record an error against every key a deployment wrote under a generated claim, so that a setting
     * which will not take effect is never accepted in silence. A generated claim reads none of them.
     *
     * The value is not compared with the one the server would have used. `claims.sub.enabled: true` is
     * what `sub` already is, and the deployment that wrote it believes it is deciding something; a rule
     * firing only where the two differ is one nobody can predict from their own file.
     */
    private fun refuseKeysWrittenOnAGeneratedClaim(
        ctx: ConfigParsingContext,
        propertiesList: List<ClaimConfigurationProperties>
    ) {
        val generatedClaimIds = GeneratedOpenIdConnectClaim.entries.map { it.id }.toSet()
        propertiesList
            .filter { it.id.normalizeClaimId() in generatedClaimIds }
            .forEach { properties ->
                val claimId = properties.id.normalizeClaimId()
                KEYS_NO_GENERATED_CLAIM_READS
                    .filterValues { valueOf -> valueOf(properties) != null }
                    .keys
                    .forEach { key ->
                        ctx.addError(
                            configExceptionOf(
                                "$CLAIMS_KEY.$claimId.$key",
                                "config.claim.generated.not_configurable",
                                "claim" to claimId
                            )
                        )
                    }
            }
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
            claimAclValidator.validateGeneratedClaimAcl(generatedClaim.scope)
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
            publishedIn = parsed.publishedIn,
            acl = acl
        )
    }

    companion object {

        /**
         * Every key `claims.<id>` accepts, paired with the value a deployment wrote under it. A generated
         * claim reads none of them.
         *
         * Everything about such a claim is this server's: [GeneratedOpenIdConnectClaim] holds its type,
         * its group, its verified id and the channels it reaches, its value is computed rather than
         * collected, and OpenID Connect Core §2 requires `sub` in every id token. Its ACL is fixed too —
         * the id token, `/userinfo` and the client claims endpoint each compute the value rather than
         * read it out of the collected claims, so the one list [ClaimAcl] would have taken from a file is
         * consulted by nothing.
         *
         * A key is spelt as Micronaut publishes it rather than read off the property behind it, so that
         * nothing here has to reproduce the hyphenation the properties class went through.
         */
        internal val KEYS_NO_GENERATED_CLAIM_READS: Map<String, (ClaimConfigurationProperties) -> Any?> = mapOf(
            "template" to { it.template },
            "enabled" to { it.enabled },
            "required" to { it.required },
            "type" to { it.type },
            "group" to { it.group },
            "verified-id" to { it.verifiedId },
            "allowed-values" to { it.allowedValues },
            "audience" to { it.audience },
            "published-in" to { it.publishedIn },
            "acl.consent-scope" to { it.acl?.consentScope },
            "acl.readable-by-user-when-consented" to { it.acl?.readableByUserWhenConsented },
            "acl.writable-by-user-when-consented" to { it.acl?.writableByUserWhenConsented },
            "acl.readable-by-client-when-consented" to { it.acl?.readableByClientWhenConsented },
            "acl.writable-by-client-when-consented" to { it.acl?.writableByClientWhenConsented },
            "acl.readable-with-client-scopes-unconditionally" to { it.acl?.readableWithClientScopesUnconditionally },
            "acl.writable-with-client-scopes-unconditionally" to { it.acl?.writableWithClientScopesUnconditionally }
        )
    }
}
