package com.sympauthy.config.validation

import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.claim.ClaimPublicationPlace
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplate
import com.sympauthy.config.parsing.ParsedClaimTemplate
import com.sympauthy.config.properties.ClaimTemplateConfigurationProperties.Companion.TEMPLATES_CLAIMS_KEY
import com.sympauthy.util.configName
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ClaimTemplatesConfigValidator(
    @Inject private val claimAclValidator: ClaimAclValidator
) {

    fun validate(
        ctx: ConfigParsingContext,
        parsed: List<ParsedClaimTemplate>,
        scopesById: Map<String, Scope>
    ): Map<String, ClaimTemplate> {
        val templates = parsed.mapNotNull { template ->
            validateTemplate(ctx, template, scopesById)
        }
        return templates.associateBy { it.id }
    }

    /**
     * Record an error against every place a template opens to a request that no request can name.
     *
     * It is the one publication rule a template can be held to on its own. A channel it names in both of
     * its lists may be the right answer for a claim overriding one of them, and `discovery` with no
     * carrier may be the right answer for a claim adding one, so both of those are the resolved claim's
     * question — [com.sympauthy.config.validation.ClaimsConfigValidator] asks them there. A place no
     * request can reach is different: nothing a claim writes afterwards can make the entry take effect,
     * so it is refused where it is written.
     *
     * The error is recorded against [ctx] rather than the child, so the template still resolves. An
     * entry that cannot take effect does not stop the rest of the template answering for the claims that
     * name it, and dropping the template would report "template not found" against every one of them
     * instead of this against the key that is wrong.
     */
    private fun refuseAPlaceNoRequestCanName(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimTemplate,
        configKeyPrefix: String
    ) {
        parsed.publishedInWhenRequested
            ?.filterNot(ClaimPublicationPlace::nameableInAClaimsRequest)
            ?.sortedBy(ClaimPublicationPlace::name)
            ?.forEach { place ->
                ctx.addError(
                    configExceptionOf(
                        "$configKeyPrefix.published-in-when-requested",
                        "config.claim.template.published_in_when_requested.not_a_channel",
                        "template" to parsed.id,
                        "place" to place.configName
                    )
                )
            }
    }

    private fun validateTemplate(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimTemplate,
        scopesById: Map<String, Scope>
    ): ClaimTemplate? {
        val configKeyPrefix = "$TEMPLATES_CLAIMS_KEY.${parsed.id}"
        val subCtx = ctx.child()

        refuseAPlaceNoRequestCanName(ctx, parsed, configKeyPrefix)

        val acl = claimAclValidator.validateTemplateAcl(subCtx, parsed.acl, configKeyPrefix, scopesById)

        ctx.merge(subCtx)
        if (subCtx.hasErrors) return null

        return ClaimTemplate(
            id = parsed.id,
            enabled = parsed.enabled,
            required = parsed.required,
            group = parsed.group,
            kind = parsed.kind,
            audienceId = parsed.audienceId,
            allowedValues = parsed.allowedValues,
            publishedIn = parsed.publishedIn,
            publishedInWhenRequested = parsed.publishedInWhenRequested,
            acl = acl
        )
    }
}
