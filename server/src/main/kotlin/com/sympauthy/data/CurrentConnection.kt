package com.sympauthy.data

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.data.connection.ConnectionStatus
import kotlinx.coroutines.currentCoroutineContext

/**
 * The connection the transaction running this coroutine is open on, and null where there is none.
 *
 * Read from the coroutine's own context rather than from the thread the same propagated context is bound to, so
 * that the answer does not turn on which thread the database resumed the caller on.
 *
 * A transaction is handed the same instance for every call inside it, closes it once it has committed or rolled
 * back, and is never handed it again: what hangs off it lives exactly as long as the transaction does.
 */
suspend fun currentConnectionOrNull(): ConnectionStatus<*>? =
    KotlinCoroutinePropagation.findPropagatedContext(currentCoroutineContext())
        ?.find(ConnectionStatus::class.java)
        ?.orElse(null)
