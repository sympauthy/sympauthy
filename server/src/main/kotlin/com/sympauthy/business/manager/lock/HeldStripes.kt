package com.sympauthy.business.manager.lock

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.connection.ConnectionSynchronization
import jakarta.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import java.util.Collections
import java.util.WeakHashMap

/**
 * The stripes every transaction in flight holds, which is what lets [LockManager] refuse a second lock
 * rather than take one out of order behind the first.
 *
 * A stripe is held until the transaction that took it commits or rolls back, which outlasts the block
 * `withLock` ran. The record has to outlast that block too, so it hangs off the connection the
 * transaction is open on rather than off the block's coroutine scope: the connection is what the database
 * holds the row lock for, every call inside one transaction is handed the same instance of it, and a
 * transaction that has ended is never handed it again — so a record that outlived its transaction can
 * refuse nobody.
 *
 * It is released by the connection being closed rather than by the transaction completing: a rollback
 * runs no completion callback at all, and throwing under the lock is what half the checks this
 * serialises do. The keys are weak on top of that, so a connection scope that ends by neither route
 * still stops pinning a connection once nothing else refers to it.
 *
 * A caller outside a transaction is answered that it holds nothing, and records nothing: the autocommit
 * that took the row released it again, and there is no later lock for that record to refuse.
 */
@Singleton
class HeldStripes {

    /** Read by `HeldStripesTest`, which is the only thing that can see a record outliving its transaction. */
    internal val byConnection: MutableMap<ConnectionStatus<*>, List<Int>> =
        Collections.synchronizedMap(WeakHashMap())

    /** The stripes the transaction this coroutine runs in already holds, in the order it took them. */
    suspend fun held(): List<Int> = connection()?.let(byConnection::get).orEmpty()

    /**
     * Record [stripe] as held by the transaction this coroutine runs in, until that transaction's
     * connection closes.
     *
     * Called once per row as that row is taken, so the record names what the transaction holds and not
     * what it set out to hold: a wait that times out part of the way through a set would otherwise leave
     * either rows no record names, or a record naming rows nothing ever locked.
     */
    suspend fun hold(stripe: Int) {
        val connection = connection() ?: return
        val held = byConnection.compute(connection) { _, alreadyHeld -> alreadyHeld.orEmpty() + stripe }
        if (held?.size == 1) {
            connection.registerSynchronization(object : ConnectionSynchronization {
                override fun beforeClosed() {
                    byConnection.remove(connection)
                }
            })
        }
    }

    /**
     * The connection the transaction running this coroutine is open on, and null where there is none.
     *
     * Read from the coroutine's own context rather than from the thread the same propagated context is
     * bound to, so that the answer does not turn on which thread the database resumed the caller on.
     */
    private suspend fun connection(): ConnectionStatus<*>? =
        KotlinCoroutinePropagation.findPropagatedContext(currentCoroutineContext())
            ?.find(ConnectionStatus::class.java)
            ?.orElse(null)
}
