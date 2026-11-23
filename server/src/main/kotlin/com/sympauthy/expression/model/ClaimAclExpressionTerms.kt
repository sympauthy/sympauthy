package com.sympauthy.expression.model

import java.time.Duration

/**
 * What an ACL expression names, read out of it when the configuration is.
 *
 * Every argument here was written as a literal, so what a door opens on is known before any request: the
 * scopes it is listed under, and the conditions a refused write is challenged for.
 */
data class ClaimAclExpressionTerms(
    /**
     * The scope of every `SCOPE_IS_CONSENTED` the expression calls, wherever it calls it.
     */
    val consentedScopes: Set<String>,
    /**
     * The scope of every `SCOPE_IS_GRANTED` the expression calls, wherever it calls it.
     */
    val grantedScopes: Set<String>,
    /**
     * The duration of every `AUTHENTICATION_IS_YOUNGER_THAN` the expression calls, wherever it calls it.
     */
    val authenticationAges: Set<Duration>,
    /**
     * The conditions meeting which can open the door.
     */
    val opening: ClaimAclOpeningTerms
)

/**
 * The conditions of an ACL expression that a different token could meet to open the door: the calls nothing
 * but `&&` and `||` stand above.
 *
 * A call under anything else is left out, whatever it is under, for the reason `docs/design/claims.md` gives.
 */
data class ClaimAclOpeningTerms(
    val consentedScopes: Set<String>,
    val authenticationAges: Set<Duration>,
    val asksForBoundToken: Boolean
)
