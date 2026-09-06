package com.sympauthy.business.manager.securitycontext

import com.sympauthy.data.model.SecurityContextEntity
import com.sympauthy.data.repository.SecurityContextRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Component in charge of deleting the security contexts whose retention has run out.
 *
 * It is a job of its own rather than work for [com.sympauthy.business.manager.flow
 * .InteractiveFlowSessionCleaner], whose transaction exists to delete a session's dependants: a context
 * is not one. It outlives every session that was seen in it, and how long it is kept turns on whether a
 * user was ever attached to it rather than on anything a session did.
 *
 * A session holding the id of a context this deleted reads as a session that was seen nowhere, and the
 * next request it makes writes the place again. It takes a deployment whose retention is shorter than
 * its own sessions to reach that, since a session being used keeps touching the contexts it carries.
 */
@Singleton
open class SecurityContextCleaner(
    @Inject private val securityContextRepository: SecurityContextRepository
) {

    /**
     * Delete every context whose expiration has passed, and answer how many there were.
     */
    @Transactional
    open suspend fun clean(): Int {
        val expiredIds = securityContextRepository.findExpired().mapNotNull(SecurityContextEntity::id)
        if (expiredIds.isEmpty()) return 0
        return securityContextRepository.deleteByIdIn(expiredIds)
    }
}
