package com.sympauthy.business.model.user.claim

/**
 * The doors of every claim a token comes through, in one [ClaimAclContext].
 *
 * It is built once per read or write and asked of each claim it covers, so the functions an expression calls
 * are bound once rather than once per claim. It is never kept past that read or write: the next one comes
 * through another token.
 *
 * [evaluate] answers whether an expression opens its door in that context. A door no expression opens is shut,
 * and is answered here without asking it.
 */
class ClaimAclTokenDoors internal constructor(private val evaluate: (ClaimAclExpression) -> Boolean) {

    private fun isOpen(door: ClaimAclExpression?): Boolean = door != null && evaluate(door)

    /**
     * Return true if the person may read [claim] through their own access token.
     */
    fun canBeReadByPerson(claim: EnabledClaim): Boolean = isOpen(claim.acl.readableByPerson)

    /**
     * Return true if the person may write [claim] through their own access token.
     *
     * A caller refused here owes the client the challenges
     * [com.sympauthy.business.manager.user.ClaimAclManager.getPersonWriteChallenges] answers rather than a
     * bare refusal.
     */
    fun canBeWrittenByPerson(claim: EnabledClaim): Boolean = isOpen(claim.acl.writableByPerson)

    /**
     * Return true if a client may read [claim].
     */
    fun canBeReadByClient(claim: EnabledClaim): Boolean = isOpen(claim.acl.readableByClient)

    /**
     * Return true if a client may write [claim].
     */
    fun canBeWrittenByClient(claim: EnabledClaim): Boolean = isOpen(claim.acl.writableByClient)
}
