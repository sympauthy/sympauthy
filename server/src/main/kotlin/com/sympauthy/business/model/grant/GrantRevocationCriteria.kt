package com.sympauthy.business.model.grant

import java.util.*

/**
 * Which grants an administrator revokes in one call.
 *
 * Each set left null narrows nothing, and the ones given narrow together: a grant is reached where it is
 * among [grantIds], held by one of [userIds], opened for one of [clientIds] and for one of [audienceIds], as
 * far as each is given. An empty set therefore reaches no grant, rather than every one.
 *
 * At least one set is given, since criteria giving none would reach every grant this server holds.
 */
data class GrantRevocationCriteria(
    val grantIds: Set<UUID>? = null,
    val userIds: Set<UUID>? = null,
    val clientIds: Set<String>? = null,
    val audienceIds: Set<String>? = null
) {

    init {
        require(listOf(grantIds, userIds, clientIds, audienceIds).any { it != null }) {
            "Criteria giving no set would reach every grant."
        }
    }
}
