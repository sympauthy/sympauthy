package com.sympauthy.config.validation

import com.sympauthy.business.model.oauth2.BuiltInClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ConsentAcl
import com.sympauthy.business.model.user.claim.UnconditionalAcl
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.model.ClaimTemplateAcl
import com.sympauthy.config.parsing.ParsedClaimAcl
import jakarta.inject.Singleton

/**
 * Validates scope references in parsed ACL data and builds final ACL models.
 */
@Singleton
class ClaimAclValidator {

    private val clientScopeIds: Set<String> by lazy {
        BuiltInClientScope.entries.map { it.scope }.toSet()
    }

    /**
     * Validate a template ACL. Checks that referenced scopes exist.
     */
    fun validateTemplateAcl(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimAcl,
        configKeyPrefix: String,
        scopesById: Map<String, Scope>
    ): ClaimTemplateAcl {
        validateConsentScope(ctx, parsed.consentScope, "$configKeyPrefix.acl.consent-scope", scopesById)
        validateClientScopeList(
            ctx,
            parsed.readableWithClientScopes,
            "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally"
        )
        validateClientScopeList(
            ctx,
            parsed.writableWithClientScopes,
            "$configKeyPrefix.acl.writable-with-client-scopes-unconditionally"
        )

        return ClaimTemplateAcl(
            consentScope = parsed.consentScope,
            readableByPersonWhenConsented = parsed.readableByPerson,
            collectedInFlowWhenConsented = parsed.collectedInFlow,
            writableByPersonWhenConsented = parsed.writableByPerson,
            readableByClientWhenConsented = parsed.readableByClient,
            writableByClientWhenConsented = parsed.writableByClient,
            readableWithClientScopesUnconditionally = parsed.readableWithClientScopes,
            writableWithClientScopesUnconditionally = parsed.writableWithClientScopes,
            writeMaxAuthenticationAge = parsed.writeMaxAuthenticationAge
        )
    }

    /**
     * Validate a full claim ACL. Checks scope references and builds the final [ClaimAcl].
     */
    fun validateAcl(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimAcl,
        configKeyPrefix: String,
        scopesById: Map<String, Scope>
    ): ClaimAcl {
        validateConsentScope(ctx, parsed.consentScope, "$configKeyPrefix.acl.consent-scope", scopesById)
        validateClientScopeList(
            ctx,
            parsed.readableWithClientScopes,
            "$configKeyPrefix.acl.readable-with-client-scopes-unconditionally"
        )
        validateClientScopeList(
            ctx,
            parsed.writableWithClientScopes,
            "$configKeyPrefix.acl.writable-with-client-scopes-unconditionally"
        )
        validateWriteMaxAuthenticationAge(ctx, parsed, configKeyPrefix)

        return ClaimAcl(
            consent = ConsentAcl(
                scope = parsed.consentScope,
                readableByPerson = parsed.readableByPerson ?: false,
                collectedInFlow = parsed.collectedInFlow ?: false,
                writableByPerson = parsed.writableByPerson ?: false,
                readableByClient = parsed.readableByClient ?: false,
                writableByClient = parsed.writableByClient ?: false,
                writeMaxAuthenticationAge = parsed.writeMaxAuthenticationAge
            ),
            unconditional = UnconditionalAcl(
                readableWithClientScopes = parsed.readableWithClientScopes ?: emptyList(),
                writableWithClientScopes = parsed.writableWithClientScopes ?: emptyList()
            )
        )
    }

    /**
     * Record an error where a maximum authentication age is set on a claim no access token may write.
     *
     * The age qualifies the write through a person's own access token and nothing else — a read is never
     * challenged, and the flow's own write is presence by definition — so on a claim that door is shut
     * for, nothing would ever ask it. It is refused rather than accepted to no effect, like every other
     * key this layer cannot honour.
     *
     * It is checked on the resolved ACL rather than on the template's, so a template naming the age and
     * a claim opening the door agree, which is what makes the key settable on a template at all.
     *
     * An age of zero or less is refused as well, whichever door is open: it would refuse every write of
     * the claim for the life of the deployment, which is not something a duration can be written to mean.
     */
    private fun validateWriteMaxAuthenticationAge(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimAcl,
        configKeyPrefix: String
    ) {
        val maxAge = parsed.writeMaxAuthenticationAge ?: return
        val configKey = "$configKeyPrefix.acl.write-max-authentication-age"
        if (parsed.writableByPerson != true) {
            ctx.addError(
                configExceptionOf(configKey, "config.claim.acl.write_max_authentication_age.not_writable")
            )
        }
        // Zero is the case a reader stumbles on: it looks like "no age at all" and means the opposite,
        // since no authentication is ever more recent than the moment it is asked about.
        if (!maxAge.isPositive) {
            ctx.addError(
                configExceptionOf(
                    configKey, "config.claim.acl.write_max_authentication_age.not_positive",
                    "duration" to maxAge.toString()
                )
            )
        }
    }

    private fun validateConsentScope(
        ctx: ConfigParsingContext,
        scope: String?,
        configKey: String,
        scopesById: Map<String, Scope>
    ) {
        if (scope == null) return
        val namedScope = scopesById[scope]
        if (namedScope is ConsentableUserScope) return
        // A scope the deployment turned off is reported apart from one that was never consentable:
        // the first is fixed by enabling it or dropping the claim, the second by correcting the name.
        val detailsId = if (namedScope?.type == ScopeType.CONSENTABLE) {
            "config.claim.acl.disabled_consent_scope"
        } else {
            "config.claim.acl.not_consentable_scope"
        }
        ctx.addError(configExceptionOf(configKey, detailsId, "scope" to scope))
    }

    private fun validateClientScopeList(ctx: ConfigParsingContext, scopes: List<String>?, configKey: String) {
        scopes?.forEachIndexed { index, scope ->
            if (scope.isBlank()) {
                ctx.addError(configExceptionOf("${configKey}[$index]", "config.empty"))
            } else if (scope !in clientScopeIds) {
                ctx.addError(
                    configExceptionOf("${configKey}[$index]", "config.claim.acl.not_client_scope", "scope" to scope)
                )
            }
        }
    }
}
