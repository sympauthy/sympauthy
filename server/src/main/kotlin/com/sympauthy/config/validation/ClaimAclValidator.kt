package com.sympauthy.config.validation

import com.sympauthy.business.model.oauth2.ClientScope
import com.sympauthy.business.model.oauth2.ConsentableUserScope
import com.sympauthy.business.model.oauth2.GrantableUserScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.oauth2.ScopeType
import com.sympauthy.business.model.user.claim.ClaimAcl
import com.sympauthy.business.model.user.claim.ClaimAclExpression
import com.sympauthy.config.ConfigParsingContext
import com.sympauthy.config.exception.configExceptionOf
import com.sympauthy.config.parsing.ParsedClaimAcl
import com.sympauthy.expression.ClaimAclExpressionTerms
import com.sympauthy.expression.ClaimAclExpressions
import com.sympauthy.expression.InvalidRuleExpressionException
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Validates the expression each door of a parsed ACL is opened by, and builds final ACL models.
 *
 * An expression is refused with the codes every expression is refused with, naming the key it was written
 * under, and so is a function written on a door that cannot answer it: the flow's door offers the consent
 * and the client alone — see [ClaimAclExpressions]. What an accepted expression names is held to the
 * configuration beside it: the scopes to the ones this deployment declares, the durations to being positive.
 */
@Singleton
class ClaimAclValidator {

    /**
     * Validate a template ACL, under the template's own keys, and build the doors it opens.
     *
     * A claim taking the template inherits them as built here rather than validating the template's text
     * again under its own key, so a template expression that cannot mean anything is reported once, where it
     * was written.
     */
    fun validateTemplateAcl(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimAcl,
        configKeyPrefix: String,
        scopesById: Map<String, Scope>
    ): ClaimAcl = validateAcl(ctx, parsed, configKeyPrefix, scopesById)

    /**
     * Validate a full claim ACL and build the final [ClaimAcl]: each door the claim wrote, validated under
     * the claim's key, and the template's for each door it wrote nothing for.
     *
     * A door written as the literal `"false"` is built shut, as one nothing opens is: it is how a claim
     * shuts a door its template opens, and the kind of the claim reads the two alike.
     */
    fun validateAcl(
        ctx: ConfigParsingContext,
        parsed: ParsedClaimAcl,
        configKeyPrefix: String,
        scopesById: Map<String, Scope>
    ): ClaimAcl {
        fun door(
            written: String?,
            inherited: ClaimAclExpression?,
            key: String,
            read: (String) -> ClaimAclExpressionTerms = ClaimAclExpressions::validateTokenExpression
        ) = if (written == null) {
            inherited
        } else {
            validateDoor(ctx, written, "$configKeyPrefix.acl.$key", scopesById, read)
        }
        val template = parsed.template
        return ClaimAcl(
            collectedInFlow = door(
                parsed.collectedInFlow,
                template.collectedInFlow,
                COLLECTED_IN_FLOW_KEY,
                ClaimAclExpressions::validateFlowExpression
            ),
            readableByPerson = door(parsed.readableByPerson, template.readableByPerson, READABLE_BY_PERSON_KEY),
            writableByPerson = door(parsed.writableByPerson, template.writableByPerson, WRITABLE_BY_PERSON_KEY),
            readableByClient = door(parsed.readableByClient, template.readableByClient, READABLE_BY_CLIENT_KEY),
            writableByClient = door(parsed.writableByClient, template.writableByClient, WRITABLE_BY_CLIENT_KEY)
        )
    }

    private fun validateDoor(
        ctx: ConfigParsingContext,
        expression: String,
        configKey: String,
        scopesById: Map<String, Scope>,
        read: (String) -> ClaimAclExpressionTerms
    ): ClaimAclExpression? {
        val terms = try {
            read(expression)
        } catch (e: InvalidRuleExpressionException) {
            ctx.addError(configExceptionOf(configKey, e.configMessageId, "message" to e.reason))
            return null
        }
        if (expression.trim().equals("false", ignoreCase = true)) return null

        terms.consentedScopes.forEach { validateConsentedScope(ctx, it, configKey, scopesById) }
        terms.grantedScopes.forEach { validateGrantedScope(ctx, it, configKey, scopesById) }
        // Every age is checked before any is acted on, so a file writing two bad ones is told about both.
        val agesAccepted = terms.authenticationAges.map { validateAuthenticationAge(ctx, it, configKey) }
        if (false in agesAccepted) return null
        return ClaimAclExpression(
            expression = expression,
            consentedScopes = terms.opening.consentedScopes,
            authenticationAges = terms.opening.authenticationAges,
            asksForBoundToken = terms.opening.asksForBoundToken
        )
    }

    private fun validateConsentedScope(
        ctx: ConfigParsingContext,
        scope: String,
        configKey: String,
        scopesById: Map<String, Scope>
    ) {
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

    private fun validateGrantedScope(
        ctx: ConfigParsingContext,
        scope: String,
        configKey: String,
        scopesById: Map<String, Scope>
    ) {
        val namedScope = scopesById[scope]
        if (namedScope is GrantableUserScope || namedScope is ClientScope) return
        // Told apart as a consented scope is: a disabled one is fixed by enabling it or dropping the
        // condition, one that was never granted by correcting the name or asking for consent instead.
        val detailsId = if (namedScope?.type == ScopeType.GRANTABLE || namedScope?.type == ScopeType.CLIENT) {
            "config.claim.acl.disabled_granted_scope"
        } else {
            "config.claim.acl.not_grantable_scope"
        }
        ctx.addError(configExceptionOf(configKey, detailsId, "scope" to scope))
    }

    /**
     * Return true if [age] may be asked of an authentication, or record why it is refused and return false.
     *
     * One of zero or less is refused: it would refuse every write of the claim for the life of the
     * deployment, since no authentication is ever more recent than the moment it is asked about.
     */
    private fun validateAuthenticationAge(ctx: ConfigParsingContext, age: Duration, configKey: String): Boolean {
        if (age.isPositive) return true
        ctx.addError(
            configExceptionOf(
                configKey, "config.claim.acl.authentication_age.not_positive", "duration" to age.toString()
            )
        )
        return false
    }

    companion object {
        const val COLLECTED_IN_FLOW_KEY = "collected-in-flow"
        const val READABLE_BY_PERSON_KEY = "readable-by-person"
        const val WRITABLE_BY_PERSON_KEY = "writable-by-person"
        const val READABLE_BY_CLIENT_KEY = "readable-by-client"
        const val WRITABLE_BY_CLIENT_KEY = "writable-by-client"
    }
}
