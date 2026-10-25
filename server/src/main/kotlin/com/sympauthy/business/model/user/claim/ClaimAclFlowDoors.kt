package com.sympauthy.business.model.user.claim

/**
 * The flow's door of every claim, for one interactive flow.
 *
 * It is built once per read and asked of each claim the read covers, so the functions an expression calls are
 * bound once rather than once per claim. It is never kept past that read: the next one binds another flow.
 *
 * [evaluate] answers whether an expression opens its door in that context. A door no expression opens is shut,
 * and is answered here without asking it.
 */
class ClaimAclFlowDoors internal constructor(private val evaluate: (ClaimAclExpression) -> Boolean) {

    private fun isOpen(door: ClaimAclExpression?): Boolean = door != null && evaluate(door)

    /**
     * Return true if the flow collects [claim].
     *
     * An identifier claim answers from its door like any other, and the flow reaches this for none of what
     * it does with one: [ClaimAcl.collectedInFlow] says why.
     */
    fun isCollectedInFlow(claim: EnabledClaim): Boolean {
        if (claim.generated) return false
        return isOpen(claim.acl.collectedInFlow)
    }
}
