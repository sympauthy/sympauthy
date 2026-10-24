package com.sympauthy.business.manager.user

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge
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
     * Return the doors the interactive flow the [client] started asks of a person who consented to
     * [consentedScopes], to be asked of as many claims as one read needs.
     */
    fun getFlowDoors(client: Client, consentedScopes: List<String>): FlowDoors =
        FlowDoors(ClaimAclExpressions.flowConfiguration(client, consentedScopes))

    /**
     * Return the doors a token comes through, evaluated in [context] at [now], to be asked of as many claims
     * as one read or write needs.
     */
    fun getTokenDoors(context: ClaimAclContext, now: LocalDateTime = LocalDateTime.now()): TokenDoors =
        TokenDoors(ClaimAclExpressions.tokenConfiguration(context, now))

    /**
     * The flow's door of every claim, for one flow.
     *
     * It is built once per read and asked of each claim the read covers, so the functions are bound once
     * rather than once per claim. It is never kept past that read: the next one binds another flow.
     */
    class FlowDoors internal constructor(private val configuration: ExpressionConfiguration) {

        /**
         * Return true if the flow collects [claim].
         *
         * An identifier claim answers from its door like any other, and the flow reaches this for none of what
         * it does with one: [com.sympauthy.business.model.user.claim.ClaimAcl.collectedInFlow] says why.
         */
        fun isCollectedInFlow(claim: EnabledClaim): Boolean {
            if (claim.generated) return false
            return isOpen(claim.acl.collectedInFlow, configuration)
        }
    }

    /**
     * The doors of every claim a token comes through, in one context.
     *
     * It is built once per read or write and asked of each claim it covers, for the reason
     * [FlowDoors] gives.
     */
    class TokenDoors internal constructor(private val configuration: ExpressionConfiguration) {

        /**
         * Return true if the person may read [claim] through their own access token.
         */
        fun canBeReadByPerson(claim: EnabledClaim): Boolean = isOpen(claim.acl.readableByPerson, configuration)

        /**
         * Return true if the person may write [claim] through their own access token.
         *
         * A caller refused here owes the client the challenges [ClaimAclManager.getPersonWriteChallenges]
         * answers rather than a bare refusal.
         */
        fun canBeWrittenByPerson(claim: EnabledClaim): Boolean = isOpen(claim.acl.writableByPerson, configuration)

        /**
         * Return true if a client may read [claim].
         */
        fun canBeReadByClient(claim: EnabledClaim): Boolean = isOpen(claim.acl.readableByClient, configuration)

        /**
         * Return true if a client may write [claim].
         */
        fun canBeWrittenByClient(claim: EnabledClaim): Boolean = isOpen(claim.acl.writableByClient, configuration)
    }

    /**
     * Return the challenges a write of [claim] through the person's own access token owes the client, where
     * [TokenDoors.canBeWrittenByPerson] refused it in [context]: one for every condition of the door's
     * expression that a different token would have met and this one did not.
     *
     * The conditions are the ones meeting which can open the door, as
     * [com.sympauthy.business.model.user.claim.ClaimAclExpression] holds them, so a condition under `!` is
     * never one a client is told to meet. Every such condition is answered whichever operand of the expression
     * it sits in, so an expression `A || B` refused on both is answered with both, and a client meeting either
     * would have been let through. That over-reports, and is what hiding which term failed behind one boolean
     * costs; the alternative is a refusal telling the client nothing to do. Empty where no condition bears — a
     * door that is shut, or one refused on a granted scope, the client or a literal — and the caller then
     * refuses by name, because there is nothing to re-authorize for.
     *
     * The age demanded is the smallest refused one, which satisfies every one of them.
     */
    fun getPersonWriteChallenges(
        claim: EnabledClaim,
        context: ClaimAclContext,
        now: LocalDateTime = LocalDateTime.now()
    ): List<WwwAuthenticateChallenge> {
        val door = claim.acl.writableByPerson ?: return emptyList()
        val authenticationAge = context.authenticationDate?.let { Duration.between(it, now) }
        val missingScopes = door.consentedScopes.filter { it !in context.consentedScopes }
        val refusedAge = door.authenticationAges
            .filter { authenticationAge == null || authenticationAge > it }
            .minOrNull()
        return listOfNotNull(
            missingScopes.takeIf { it.isNotEmpty() }?.let { WwwAuthenticateChallenge.InsufficientScope(it) },
            refusedAge?.let { WwwAuthenticateChallenge.InsufficientUserAuthentication(it) },
            WwwAuthenticateChallenge.UnboundToken.takeIf { door.asksForBoundToken && !context.bound }
        )
    }
}

/**
 * Return true if [door] is open under [configuration]: a door no expression opens is shut.
 */
private fun isOpen(door: ClaimAclExpression?, configuration: ExpressionConfiguration): Boolean {
    door ?: return false
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
