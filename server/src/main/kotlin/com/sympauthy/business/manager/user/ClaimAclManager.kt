package com.sympauthy.business.manager.user

import com.ezylang.evalex.config.ExpressionConfiguration
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.EnabledScope
import com.sympauthy.business.model.oauth2.WwwAuthenticateChallenge
import com.sympauthy.business.model.user.claim.ClaimAclContext
import com.sympauthy.business.model.user.claim.ClaimAclExpression
import com.sympauthy.business.model.user.claim.ClaimAclFlowDoors
import com.sympauthy.business.model.user.claim.ClaimAclTokenDoors
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
    fun getFlowDoors(client: Client, consentedScopes: List<String>): ClaimAclFlowDoors {
        val configuration = ClaimAclExpressions.flowConfiguration(client, consentedScopes)
        return ClaimAclFlowDoors { door -> evaluate(door, configuration) }
    }

    /**
     * Return the doors a token comes through, evaluated in [context] at [now], to be asked of as many claims
     * as one read or write needs.
     */
    fun getTokenDoors(context: ClaimAclContext, now: LocalDateTime = LocalDateTime.now()): ClaimAclTokenDoors {
        val configuration = ClaimAclExpressions.tokenConfiguration(context, now)
        return ClaimAclTokenDoors { door -> evaluate(door, configuration) }
    }

    /**
     * Return the challenges a write of [claim] through the person's own access token owes the client, where
     * [ClaimAclTokenDoors.canBeWrittenByPerson] refused it in [context]: one for every condition of the door's
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
     * A scope the client is not allowed is never named: the authorization it asks for next would drop it, and
     * the client would be refused again for a scope it was told would let it through.
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
        val allowedScopes = context.client.allowedScopes?.map(EnabledScope::scope)?.toSet()
        val missingScopes = door.consentedScopes.filter {
            it !in context.consentedScopes && (allowedScopes == null || it in allowedScopes)
        }
        val refusedAge = door.authenticationAges
            .filter { authenticationAge == null || authenticationAge > it }
            .minOrNull()
        return listOfNotNull(
            missingScopes.takeIf { it.isNotEmpty() }?.let { WwwAuthenticateChallenge.InsufficientScope(it) },
            refusedAge?.let { WwwAuthenticateChallenge.InsufficientUserAuthentication(it) },
            WwwAuthenticateChallenge.UnboundToken.takeIf { door.asksForBoundToken && !context.bound }
        )
    }

    /**
     * Return the challenges one write of several claims through the person's own access token owes the
     * client, from what [getPersonWriteChallenges] answered for each of them in [challengesByClaim], as one
     * refusal.
     *
     * A token meeting the answer meets every claim's, so it names every scope any of them names, the smallest
     * age any of them demands, and the binding once. A claim owing no challenge contributes none, so a caller
     * holding one refuses it by name before asking this.
     */
    fun mergePersonWriteChallenges(
        challengesByClaim: Collection<List<WwwAuthenticateChallenge>>
    ): List<WwwAuthenticateChallenge> {
        val challenges = challengesByClaim.flatten()
        val missingScopes = challenges.filterIsInstance<WwwAuthenticateChallenge.InsufficientScope>()
            .flatMap(WwwAuthenticateChallenge.InsufficientScope::scopes)
            .distinct()
        val refusedAge = challenges.filterIsInstance<WwwAuthenticateChallenge.InsufficientUserAuthentication>()
            .minOfOrNull(WwwAuthenticateChallenge.InsufficientUserAuthentication::maxAge)
        return listOfNotNull(
            missingScopes.takeIf { it.isNotEmpty() }?.let { WwwAuthenticateChallenge.InsufficientScope(it) },
            refusedAge?.let { WwwAuthenticateChallenge.InsufficientUserAuthentication(it) },
            WwwAuthenticateChallenge.UnboundToken.takeIf { WwwAuthenticateChallenge.UnboundToken in challenges }
        )
    }
}

/**
 * Return true if [door] opens under [configuration].
 */
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
