package com.sympauthy.business.manager.lock

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.ConnectionSynchronization
import jakarta.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The stripes every transaction in flight holds, which is what lets [LockManager] refuse a second lock
 * rather than take one out of order behind the first.
 *
 * A stripe is held until the transaction that took it commits or rolls back, which outlasts the block
 * `withLock` ran. The record has to outlast that block too, so it hangs off the connection the
 * transaction is open on rather than off the block's coroutine scope: the connection is what the database
 * holds the row lock for, every call inside one transaction is handed the same instance of it, and a
 * transaction that has ended is never handed it again. Nothing releases a record by hand and nothing can
 * forget to — the connection's own completion does it, and a record left behind by a completion that
 * never came names a connection no later transaction can be given.
 *
 * A caller outside a transaction holds nothing, and is told so: the autocommit that took the row released
 * it, and there is no second lock to refuse.
 */
@Singleton
class HeldStripes {

    private val byConnection = ConcurrentHashMap<ConnectionStatus<*>, List<Int>>()

    /** The stripes the transaction this coroutine runs in already holds, in the order they were taken. */
    suspend fun held(): List<Int> = connection()?.let(byConnection::get).orEmpty()

    /**
     * Record [stripes] as held by the transaction this coroutine runs in, until that transaction ends.
     *
     * A call naming none records nothing: a transaction holding no stripe is ordered against nobody, and
     * stays free to take a set later.
     */
    suspend fun hold(stripes: List<Int>) {
        if (stripes.isEmpty()) return
        val connection = connection() ?: return
        byConnection[connection] = stripes
        connection.registerSynchronization(object : ConnectionSynchronization {
            override fun executionComplete() {
                byConnection.remove(connection)
            }
        })
    }

    /**
     * The connection the transaction running this coroutine is open on, and null where there is no
     * transaction.
     *
     * Read from the coroutine's own context rather than from the thread the same propagated context is
     * bound to, so that the answer does not turn on which thread the database resumed the caller on.
     */
    private suspend fun connection(): ConnectionStatus<*>? =
        KotlinCoroutinePropagation.findPropagatedContext(currentCoroutineContext())
            ?.find(ConnectionStatus::class.java)
            ?.orElse(null)
}
