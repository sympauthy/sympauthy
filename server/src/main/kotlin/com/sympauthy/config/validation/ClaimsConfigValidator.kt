package com.sympauthy.config.validation

import com.sympauthy.business.audience.model.Audience
import com.sympauthy.business.claim.model.ApplicationClaim
import com.sympauthy.business.claim.model.Claim
import com.sympauthy.business.claim.model.ClaimAcl
import com.sympauthy.business.claim.model.ClaimDataType
import com.sympauthy.business.claim.model.ClaimKind
import com.sympauthy.business.claim.model.ClaimPublicationPlace
import com.sympauthy.business.claim.model.DisabledClaim
import com.sympauthy.business.claim.model.GeneratedClaim
import com.sympauthy.business.claim.model.GeneratedOpenIdConnectClaim
import com.sympauthy.business.claim.model.PersonalClaim
import com.sympauthy.business.scope.model.Scope
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.parsing.ParsedClaim
import com.sympauthy.config.parsing.normalizeClaimId
import com.sympauthy.config.properties.ClaimConfigurationProperties.Companion.CLAIMS_KEY
import com.sympauthy.platform.util.configName
import com.sympauthy.platform.util.wireName
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
        identifierClaims: List<String>?
    ): List<Claim> {
        refuseKeysWrittenOnAGeneratedClaim(ctx, writtenKeys)

        val identifierClaimIds = identifierClaims.orEmpty().toSet()
        val claims = parsed.mapNotNull { parsedClaim ->
            validateClaim(ctx, parsedClaim, audiencesById, scopesById, identifierClaimIds)
        }

        // An identifier claim belongs to every audience, and `docs/design/claims.md` says why
        // restricting one is refused.
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
            // An identifier claim is a personal claim (`docs/design/claims.md`). The kind is read
            // resolved, so a claim taking `application` from its template is refused on the key
            // where the deployment overrides it.
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

        return claims
    }

    /**
     * Record an error against every one of the [writtenKeys] a deployment wrote under a generated
     * claim, naming the key as the file spells it. A generated claim reads none of them, so each
     * one is refused whatever is written under it, a key with nothing under it included.
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
     * Record an error where a claim is advertised in the discovery document and carried by no place
     * at all, which `docs/design/claims.md` refuses.
     *
     * A channel opened to a request counts as a carrier where a request can reach it. A place
     * [refuseAPlaceNoRequestCanName] is about to refuse carries nothing, so it is not one —
     * counting it would hide this error behind that one and report a file naming both mistakes one
     * restart at a time.
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
        val carriers = parsed.publishedIn +
                parsed.publishedInWhenRequested.filter(ClaimPublicationPlace::nameableInAClaimsRequest)
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
     * Record an error against every place a claim opens to a request that no request can name,
     * which [ClaimPublicationPlace.nameableInAClaimsRequest] answers. `docs/design/claims.md` says
     * why such a place is refused.
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
     * Record an error against every ACL key the [ParsedClaim.kind] of [parsed] cannot mean: the
     * client write door of a personal claim restricted to no audience, and the flow's door and the
     * person's write door of an application claim, which `docs/design/claims.md` refuses. A claim
     * of neither kind is held to nothing here.
     *
     * [acl] is the resolved ACL, and the audience and the kind are read resolved too, so a grant, a
     * restriction or a kind reaching the claim through a template is held to this like one written
     * on the claim. A door is held to this when it resolved to anything but nothing or the literal
     * `"false"`, whatever its expression asks, and each door refused is named, so a deployment
     * shutting one of two is told about the other rather than refused twice over; the key named is
     * the claim's own, which is where an operator overrides what a template offers.
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
                if (acl.writableByClient != null) {
                    refuse(
                        ClaimAclValidator.WRITABLE_BY_CLIENT_KEY,
                        "config.claim.kind.shared_personal_claim_client_write"
                    )
                }
            }

            ClaimKind.APPLICATION -> {
                if (acl.collectedInFlow != null) {
                    refuse(
                        ClaimAclValidator.COLLECTED_IN_FLOW_KEY,
                        "config.claim.kind.application_claim_person_write"
                    )
                }
                if (acl.writableByPerson != null) {
                    refuse(
                        ClaimAclValidator.WRITABLE_BY_PERSON_KEY,
                        "config.claim.kind.application_claim_person_write"
                    )
                }
            }

            // A generated claim, which is of neither kind and whose ACL no file spoke for — or a claim
            // whose file says whose it is nowhere, which the parser already refused.
            null -> Unit
        }
    }

    /**
     * Record an error against every key the type of [parsed] cannot mean, and answer the size its values
     * are held to: the `max-size` it resolved to, else the [ClaimDataType.defaultMaxSize] of its type, and
     * null for a type that cannot have one.
     *
     * Each key is held to what [dataType] declares, on the resolved claim, so a value reaching it
     * through a template is refused like one written on the claim, and the error names the claim's
     * own key. A `max-size` that is not greater than zero is refused too, because no value is that
     * small.
     */
    private fun refuseWhatTheTypeCannotMean(
        ctx: ConfigParsingContext,
        parsed: ParsedClaim,
        dataType: ClaimDataType,
        acl: ClaimAcl,
        configKeyPrefix: String
    ): Int? {
        fun refuse(key: String, messageId: String) = ctx.addError(
            configExceptionOf("$configKeyPrefix.$key", messageId, "claim" to parsed.id, "type" to dataType.wireName)
        )
        if (parsed.required && !dataType.canBeRequired) {
            refuse("required", "config.claim.type.required")
        }
        if (acl.collectedInFlow != null && !dataType.canBeCollectedInFlow) {
            refuse("acl.${ClaimAclValidator.COLLECTED_IN_FLOW_KEY}", "config.claim.type.collected_in_flow")
        }
        if (parsed.group != null && !dataType.canBeGrouped) {
            refuse("group", "config.claim.type.group")
        }
        if (parsed.verifiedId != null && !dataType.canBeVerified) {
            refuse("verified-id", "config.claim.type.verified_id")
        }
        if (parsed.allowedValues != null && !dataType.canBeEnumerated) {
            refuse("allowed-values", "config.claim.type.allowed_values")
        }
        if (!dataType.canHaveMaxSize) {
            if (parsed.maxSize != null) {
                refuse("max-size", "config.claim.type.max_size")
            }
            return null
        }
        val maxSize = parsed.maxSize ?: return dataType.defaultMaxSize
        if (maxSize <= 0) {
            refuse("max-size", "config.claim.max_size.not_positive")
            return null
        }
        return maxSize
    }

    /**
     * Record an error where [parsed] is required and its `acl.collected-in-flow` door resolved to
     * nothing or to the literal `"false"`, so that no flow will ever collect it, which
     * `docs/design/claims.md` refuses. A door opening on a condition is not refused, and neither is
     * an identifier claim, which the sign-up collects whatever its door says. A type that cannot be
     * required at all has already been refused for that by [refuseWhatTheTypeCannotMean], and is
     * not refused a second time.
     */
    private fun refuseRequiredTheFlowNeverCollects(
        ctx: ConfigParsingContext,
        parsed: ParsedClaim,
        dataType: ClaimDataType,
        acl: ClaimAcl,
        identifierClaimIds: Set<String>,
        configKeyPrefix: String
    ) {
        if (!parsed.required || !dataType.canBeRequired) return
        if (acl.collectedInFlow != null || parsed.id in identifierClaimIds) return
        ctx.addError(
            configExceptionOf(
                "$configKeyPrefix.required",
                "config.claim.required.never_collected",
                "claim" to parsed.id
            )
        )
    }

    private fun validateClaim(
        ctx: ConfigParsingContext,
        parsed: ParsedClaim,
        audiencesById: Map<String, Audience>,
        scopesById: Map<String, Scope>,
        identifierClaimIds: Set<String>
    ): Claim? {
        val configKeyPrefix = "$CLAIMS_KEY.${parsed.id}"

        ctx.refuseReservedIdentifier(configKeyPrefix, parsed.id)

        refuseAdvertisedWithoutACarrier(ctx, parsed, configKeyPrefix)

        refuseAChannelInBothPublicationLists(ctx, parsed, configKeyPrefix)

        refuseAPlaceNoRequestCanName(ctx, parsed, configKeyPrefix)

        // Validate audience cross-reference.
        val audienceId = resolveAudience(
            ctx, parsed.audienceId, audiencesById,
            "$configKeyPrefix.audience", "config.claim.audience.not_found"
        )?.id

        // Validate ACL scope references.
        val acl = if (parsed.generated) {
            val generatedClaim = GeneratedOpenIdConnectClaim.entries.first { it.id == parsed.id }
            generatedClaim.acl
        } else {
            claimAclValidator.validateAcl(ctx, parsed.acl, configKeyPrefix, scopesById)
        }

        refuseAclTheKindCannotMean(ctx, parsed, acl, configKeyPrefix)

        val dataType = parsed.dataType ?: return null

        val maxSize = refuseWhatTheTypeCannotMean(ctx, parsed, dataType, acl, configKeyPrefix)

        refuseRequiredTheFlowNeverCollects(ctx, parsed, dataType, acl, identifierClaimIds, configKeyPrefix)

        if (parsed.generated) {
            return GeneratedClaim(
                id = parsed.id,
                verifiedId = parsed.verifiedId,
                dataType = dataType,
                group = parsed.group,
                publishedIn = parsed.publishedIn,
                acl = acl
            )
        }
        // A claim whose file says whose it is nowhere, which the parser already refused.
        val kind = parsed.kind ?: return null
        if (!parsed.enabled) {
            return DisabledClaim(
                id = parsed.id,
                verifiedId = parsed.verifiedId,
                dataType = dataType,
                kind = kind,
                group = parsed.group,
                required = parsed.required,
                allowedValues = parsed.allowedValues,
                maxSize = maxSize,
                audienceId = audienceId,
                acl = acl
            )
        }
        return when (kind) {
            ClaimKind.PERSONAL -> PersonalClaim(
                id = parsed.id,
                verifiedId = parsed.verifiedId,
                dataType = dataType,
                group = parsed.group,
                required = parsed.required,
                allowedValues = parsed.allowedValues,
                maxSize = maxSize,
                audienceId = audienceId,
                publishedIn = parsed.publishedIn,
                publishedInWhenRequested = parsed.publishedInWhenRequested,
                acl = acl
            )

            ClaimKind.APPLICATION -> ApplicationClaim(
                id = parsed.id,
                verifiedId = parsed.verifiedId,
                dataType = dataType,
                group = parsed.group,
                required = parsed.required,
                allowedValues = parsed.allowedValues,
                maxSize = maxSize,
                audienceId = audienceId,
                publishedIn = parsed.publishedIn,
                publishedInWhenRequested = parsed.publishedInWhenRequested,
                acl = acl
            )
        }
    }
}
