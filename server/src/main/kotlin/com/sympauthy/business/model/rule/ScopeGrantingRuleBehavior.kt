package com.sympauthy.business.model.rule

/**
 * What a [ScopeGrantingRule] does to the scopes it names once all of its expressions match.
 *
 * A [DECLINE] wins over a [GRANT] of equal priority, so the two are not symmetric.
 */
enum class ScopeGrantingRuleBehavior {
    GRANT,
    DECLINE,
}
