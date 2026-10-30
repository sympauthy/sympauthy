package com.sympauthy.business.model.grant

import java.util.*

/**
 * Which grants an administrator revokes in one call.
 *
 * Each set left null narrows nothing, and the ones given narrow together: a grant is reached where it is
 * among [grantIds], held by one of [userIds], opened for one of [clientIds] and for one of [audienceIds], as
 * far as each is given. An empty set therefore reaches no grant, rather than every one.
 */
data class GrantRevocationCriteria(
    val grantIds: Set<UUID>? = null,
    val userIds: Set<UUID>? = null,
    val clientIds: Set<String>? = null,
    val audienceIds: Set<String>? = null
) {

    /**
     * Whether no set is given, which would reach every grant this server holds.
     */
    val isUnbounded: Boolean
        get() = grantIds == null && userIds == null && clientIds == null && audienceIds == null

    /**
     * Whether [grant] is one these criteria reach, live or not.
     */
    fun matches(grant: Grant): Boolean {
        return (grantIds == null || grant.id in grantIds) &&
            (userIds == null || grant.userId in userIds) &&
            (clientIds == null || grant.clientId in clientIds) &&
            (audienceIds == null || grant.audienceId in audienceIds)
    }
}
