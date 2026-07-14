package com.sympauthy.business.model.rule

/**
 * Behavior of an [ActAsRule] when all its expressions are matched for a given (acting client, target user) pair.
 */
enum class ActAsRuleBehavior {
    /**
     * Permit the acting client to obtain an act-as token for the target user.
     */
    ALLOW,

    /**
     * Forbid the acting client from obtaining an act-as token for the target user.
     */
    DENY,
}
