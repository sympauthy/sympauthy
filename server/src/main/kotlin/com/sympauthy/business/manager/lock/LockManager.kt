package com.sympauthy.business.manager.lock

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.data.repository.ObjectLockRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Takes the lock two transactions have to agree on before either of them may act on what it names.
 *
 * It is the mechanism for a rule no constraint can express: one spanning several tables, or one over a
 * value whose competitor has no row yet. Where a single column can hold the value uniquely, a unique
 * index says it better and this manager is the wrong tool.
 *
 * A lock is a row of `object_locks` held for the length of a transaction. Nothing releases it by hand
 * and nothing can forget to: the commit or the rollback does it.
 */
@Singleton
open class LockManager(
    @Inject private val objectLockRepository: ObjectLockRepository,
    @Inject private val heldStripes: HeldStripes
) {

    /**
     * Run [block] holding the lock over every one of [keys], and answer what it answered.
     *
     * The lock is held until the enclosing transaction ends rather than until [block] returns, so a
     * caller inside one keeps it until its own commit — which is the point: the check this serialises
     * and the write it protects have to be inside the same lock, and the caller is what spans them.
     * `@Transactional` is what makes a caller outside a transaction correct too, by giving the block one
     * instead of leaving it a lock the autocommit released before the block began.
     *
     * The stripes are taken in ascending order, so two callers whose sets of keys overlap take the
     * shared rows in the same order and cannot deadlock over them.
     *
     * **Every object a transaction will touch is named in one call.** A second call in one transaction —
     * inside the first block or issued after it returned, which is the same lock either way — is the one
     * ordering this cannot impose, so it is refused unless the first already covers it: `lock.second`,
     * which is the server's own failure and not the caller's. A call naming no key takes nothing and
     * refuses nothing after it.
     */
    @Transactional
    open suspend fun <T> withLock(vararg keys: LockKey, block: suspend () -> T): T {
        val stripes = keys.map(LockKey::stripe).distinct().sorted()
        val held = heldStripes.held()

        if (held.isNotEmpty()) {
            if (!held.containsAll(stripes)) {
                throw internalBusinessExceptionOf(
                    detailsId = "lock.second",
                    "held" to held.joinToString(),
                    "requested" to stripes.joinToString()
                )
            }
            return block()
        }

        // Recorded ahead of being taken: a wait that times out part of the way through the set leaves the
        // transaction holding the rows it did take, and a record written afterwards would name none of them.
        heldStripes.hold(stripes)
        stripes.forEach { objectLockRepository.lock(it) }
        return block()
    }
}
