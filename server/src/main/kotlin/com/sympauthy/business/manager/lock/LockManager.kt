package com.sympauthy.business.manager.lock

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.data.repository.ObjectLockRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

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
    @Inject private val objectLockRepository: ObjectLockRepository
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
     * **Every object a transaction will touch is named in one call.** A second call inside the first is
     * the one ordering this cannot impose, so it is refused unless the first already covers it —
     * `lock.nested`, which is the server's own failure and not the caller's.
     */
    @Transactional
    open suspend fun <T> withLock(vararg keys: LockKey, block: suspend () -> T): T {
        val stripes = keys.map(LockKey::stripe).distinct().sorted()
        val held = currentCoroutineContext()[HeldStripes]

        if (held != null) {
            if (!held.stripes.containsAll(stripes)) {
                throw internalBusinessExceptionOf(
                    detailsId = "lock.nested",
                    "held" to held.stripes.joinToString(),
                    "requested" to stripes.joinToString()
                )
            }
            return block()
        }

        stripes.forEach { objectLockRepository.lock(it) }
        return withContext(HeldStripes(stripes)) { block() }
    }
}

/**
 * The stripes the transaction running this coroutine already holds.
 *
 * Carried in the coroutine context rather than in a field, because what holds a lock is a transaction
 * and what a transaction is, here, is a coroutine: two requests served by one instance share the
 * manager and must not share what it believes it is holding.
 */
internal class HeldStripes(
    val stripes: List<Int>
) : AbstractCoroutineContextElement(HeldStripes) {

    companion object Key : CoroutineContext.Key<HeldStripes>
}
