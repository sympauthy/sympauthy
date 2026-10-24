package com.sympauthy.business.manager.user

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.user.claim.ClaimAclChallenge
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimAclExpression
import com.sympauthy.business.model.user.claim.EnabledClaim
import com.sympauthy.expression.ClaimAclExpressions
import com.sympauthy.expression.InvalidRuleExpressionException
import com.sympauthy.expression.evaluateRuleExpression
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDateTime

/**
 * Answers whether a door of a claim's ACL is open to a caller, by evaluating the expression the deployment
 * wrote on it.
 *
 * Whether an expression is usable at all is settled once, when the configuration is read, by
 * [ClaimAclExpressions]. What is left here is the half that needs a request behind it: binding the functions
 * to the context the door is asked in, and running them. A door no expression opens is shut to everybody.
 *
 * Each door is asked by the place that reads or writes through it and by no other: `docs/design/claims.md`
 * is where the places are told apart.
 *
 * An expression that passed validation and fails here is the server's own failure, and is thrown as an
 * internal [com.sympauthy.business.exception.BusinessException] under `expression.evaluate.failed` or
 * `expression.evaluate.invalid_return`.
 */
@Singleton
class ClaimAclManager {

    /**
     * Return true if the interactive flow the [client] started collects [claim] from a person who consented to
     * [consentedScopes].
     *
     * An identifier claim answers from its door like any other, and the flow reaches this for none of what it
     * does with one: [com.sympauthy.business.model.user.claim.ClaimAcl.collectedInFlow] says why.
     */
    fun isCollectedInFlow(claim: EnabledClaim, client: Client, consentedScopes: List<String>): Boolean {
        if (claim.generated) return false
        val door = claim.acl.collectedInFlow ?: return false
        return evaluate(door, ClaimAclExpressions.flowConfiguration(client, consentedScopes))
    }

    /**
     * Return true if the person may read [claim] through their own access token, in [context].
     */
    fun canBeReadByPerson(
        claim: EnabledClaim,
        context: ClaimAclContext,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean = isOpen(claim.acl.readableByPerson, context, now)

    /**
     * Return true if the person may write [claim] through their own access token, in [context].
     *
     * A caller refused here owes the client the challenges [getPersonWriteChallenges] answers rather than a
     * bare refusal.
     */
    fun canBeWrittenByPerson(
        claim: EnabledClaim,
        context: ClaimAclContext,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean = isOpen(claim.acl.writableByPerson, context, now)

    /**
     * Return true if a client may read [claim], in [context].
     */
    fun canBeReadByClient(
        claim: EnabledClaim,
        context: ClaimAclContext,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean = isOpen(claim.acl.readableByClient, context, now)

    /**
     * Return true if a client may write [claim], in [context].
     */
    fun canBeWrittenByClient(
        claim: EnabledClaim,
        context: ClaimAclContext,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean = isOpen(claim.acl.writableByClient, context, now)

    /**
     * Return the challenges a write of [claim] through the person's own access token owes the client, where
     * [canBeWrittenByPerson] refused it in [context]: one for every condition the door's expression names that
     * a different token would have met and this one did not.
     *
     * Every such condition is answered whichever operand of the expression it sits in, so an expression
     * `A || B` refused on both is answered with both, and a client meeting either would have been let
     * through. That over-reports, and is what hiding which term failed behind one boolean costs; the
     * alternative is a refusal telling the client nothing to do. Empty where no condition bears — a door
     * that is shut, or one refused on a granted scope, the client or a literal — and the caller then refuses
     * by name, because there is nothing to re-authorize for.
     *
     * The age demanded is the smallest refused one, which satisfies every one of them.
     */
    fun getPersonWriteChallenges(
        claim: EnabledClaim,
        context: ClaimAclContext,
        now: LocalDateTime = LocalDateTime.now()
    ): List<ClaimAclChallenge> {
        val door = claim.acl.writableByPerson ?: return emptyList()
        val authenticationAge = context.authenticationDate?.let { Duration.between(it, now) }
        val missingScopes = door.consentedScopes.filter { it !in context.consentedScopes }
        val refusedAge = door.authenticationAges
            .filter { authenticationAge == null || authenticationAge > it }
            .minOrNull()
        return listOfNotNull(
            missingScopes.takeIf { it.isNotEmpty() }?.let { ClaimAclChallenge.InsufficientScope(it) },
            refusedAge?.let { ClaimAclChallenge.InsufficientUserAuthentication(it) },
            ClaimAclChallenge.UnboundToken.takeIf { door.asksForBoundToken && !context.bound }
        )
    }

    private fun isOpen(door: ClaimAclExpression?, context: ClaimAclContext, now: LocalDateTime): Boolean {
        door ?: return false
        return evaluate(door, ClaimAclExpressions.tokenConfiguration(context, now))
    }

    private fun evaluate(door: ClaimAclExpression, configuration: ExpressionConfiguration): Boolean {
        return try {
            evaluateRuleExpression(door.expression, configuration)
        } catch (e: InvalidRuleExpressionException) {
            throw internalBusinessExceptionOf(
                detailsId = e.businessErrorDetailsId,
                throwable = e,
                "message" to e.reason
            )
        }
    }
}
