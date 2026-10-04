package com.sympauthy.config.validation

import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimKind
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.business.model.user.claim.GeneratedOpenIdConnectClaim
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.parsing.ParsedClaim
import com.sympauthy.config.parsing.normalizeClaimId
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import com.sympauthy.util.configName
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
            // An identifier claim is the person's: it is what the account signs in with, and nobody but
            // the person ever types it. It is read off the resolved kind, so a claim taking `application`
            // from a template — or from the silence every bare claim falls through to — is refused on the
            // key where the deployment overrides it.
            if (identifierClaim != null && identifierClaim.kind == ClaimKind.APPLICATION) {
                ctx.addError(
                    configExceptionOf(
                        "$CLAIMS_KEY.$identifierClaimId.kind",
                        "config.claim.kind.identifier_claim_not_personal",
                        "claim" to identifierClaimId
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
     * **A channel opened to a request counts as a carrier.** A client reading the name out of the document
     * can ask for it and be answered, which is the whole of what this rule asks of a carrier; refusing
     * such a claim would make a deployment choose between advertising a name and only answering it on
     * request.
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
        val carriers = parsed.publishedIn + parsed.publishedInWhenRequested
        if (carriers.any(ClaimPublicationPlace::carriesAValue)) return
        ctx.addError(
            configExceptionOf(
                "$configKeyPrefix.published-in",
                "config.claim.published_in.advertised_without_a_carrier",
                "claim" to parsed.id
            )
        )
    }

    /**
     * Record an error against every channel a claim names in both of its publication lists.
     *
     * Always and on-request are two answers for one channel and only one of them can be meant: a channel
     * in `published-in` carries the value for every request, which leaves `published-in-when-requested`
     * saying nothing about it, so a deployment writing both has either opened a channel it meant to keep
     * shut or asked for a condition that will never be applied.
     *
     * Each channel is named, so a file naming two reports both, and the error names
     * `published-in-when-requested` because that is the list whose entry has no effect.
     *
     * It is checked on the resolved sets rather than on the claim's own entries, for the reason
     * [refuseAdvertisedWithoutACarrier] gives: a claim inheriting one list from a template is held to this
     * like a claim writing both itself.
     */
    private fun refuseAChannelInBothPublicationLists(
        ctx: ConfigParsingContext,
        parsed: ParsedClaim,
        configKeyPrefix: String
    ) {
        parsed.publishedInWhenRequested
            .filter { it in parsed.publishedIn }
            .sortedBy(ClaimPublicationPlace::name)
            .forEach { place ->
                ctx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.published-in-when-requested",
                        "config.claim.published_in_when_requested.already_published",
                        "claim" to parsed.id,
                        "place" to place.configName
                    )
                )
            }
    }

    /**
     * Record an error against every place a claim opens to a request that no request can name.
     *
     * OpenID Connect Core §5.5 defines a member for the two OpenID channels and for no other place, so a
     * claim opening the access token or the introspection response to a request has asked for a condition
     * nothing will ever satisfy — the value is published there always or not at all.
     * [ClaimPublicationPlace.nameableInAClaimsRequest] is which places a request can reach.
     */
    private fun refuseAPlaceNoRequestCanName(
        ctx: ConfigParsingContext,
        parsed: ParsedClaim,
        configKeyPrefix: String
    ) {
        parsed.publishedInWhenRequested
            .filterNot(ClaimPublicationPlace::nameableInAClaimsRequest)
            .sortedBy(ClaimPublicationPlace::name)
            .forEach { place ->
                ctx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.published-in-when-requested",
                        "config.claim.published_in_when_requested.not_a_channel",
                        "claim" to parsed.id,
                        "place" to place.configName
                    )
                )
            }
    }

    /**
     * Record an error against every ACL key the [ParsedClaim.kind] of [parsed] cannot mean.
     *
     * A personal claim restricted to no audience grants no client write: a client of one audience setting
     * `name` would be choosing what every other audience is told a person is called, over the person's head
     * and with the person never asked. Restricted to one audience the value leaves this server to that
     * audience alone, and the clients of an audience already trust each other with what it owns, so the
     * grant is refused on the shared claim alone. An application claim grants neither of the person's
     * writes, because a backend answers for its value and nobody asks a person to type their credit score.
     * A claim of neither kind is held to nothing here: this server computes its value, or its file never
     * said whose the value is, and the parser refused that.
     *
     * [acl] is the resolved ACL, and the audience and the kind are read resolved too, so a grant, a
     * restriction or a kind reaching the claim through a template is held to this like one written on the
     * claim — which is what makes all three settable on a template at all. Each key that grants is named,
     * so a deployment removing one of two is told about the other rather than refused twice over, and the
     * key named is the claim's own, which is where an operator overrides what a template offers.
     */
    private fun refuseAclTheKindCannotMean(
        ctx: ConfigParsingContext,
        parsed: ParsedClaim,
        acl: ClaimAcl,
        configKeyPrefix: String
    ) {
        fun refuse(key: String, messageId: String) = ctx.addError(
            configExceptionOf("$configKeyPrefix.acl.$key", messageId, "claim" to parsed.id)
        )
        when (parsed.kind) {
            ClaimKind.PERSONAL -> {
                // The audience the claim resolved to, which a template may have supplied, and not the one
                // validated against the audiences this deployment declares: a claim naming an audience
                // that does not exist is refused for that, and reading the validated null here would
                // refuse it a second time for being shared when its file says it is not.
                if (parsed.audienceId != null) return
                if (acl.consent.writableByClient) {
                    refuse(
                        "writable-by-client-when-consented",
                        "config.claim.kind.shared_personal_claim_client_write"
                    )
                }
                if (acl.unconditional.writableWithClientScopes.isNotEmpty()) {
                    refuse(
                        "writable-with-client-scopes-unconditionally",
                        "config.claim.kind.shared_personal_claim_client_write"
                    )
                }
            }

            ClaimKind.APPLICATION -> {
                if (acl.consent.collectedInFlow) {
                    refuse(
                        "collected-in-flow-when-consented",
                        "config.claim.kind.application_claim_person_write"
                    )
                }
                if (acl.consent.writableByPerson) {
                    refuse(
                        "writable-by-person-when-consented",
                        "config.claim.kind.application_claim_person_write"
                    )
                }
            }

            // A generated claim, which is of neither kind and whose ACL no file spoke for — or a claim
            // whose file says whose it is nowhere, which the parser already refused.
            null -> Unit
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

        refuseAdvertisedWithoutACarrier(ctx, parsed, configKeyPrefix)

        refuseAChannelInBothPublicationLists(ctx, parsed, configKeyPrefix)

        refuseAPlaceNoRequestCanName(ctx, parsed, configKeyPrefix)

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

        refuseAclTheKindCannotMean(ctx, parsed, acl, configKeyPrefix)

        if (parsed.dataType == null) return null

        return Claim(
            id = parsed.id,
            enabled = parsed.enabled,
            verifiedId = parsed.verifiedId,
            dataType = parsed.dataType,
            kind = parsed.kind,
            group = parsed.group,
            required = parsed.required,
            generated = parsed.generated,
            collectedInFlow = if (!parsed.generated) acl.consent.collectedInFlow else false,
            allowedValues = parsed.allowedValues,
            audienceId = audienceId,
            publishedIn = parsed.publishedIn,
            publishedInWhenRequested = parsed.publishedInWhenRequested,
            acl = acl
        )
    }
}
