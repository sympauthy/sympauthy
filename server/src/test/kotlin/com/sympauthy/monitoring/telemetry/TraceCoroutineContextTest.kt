package com.sympauthy.monitoring.telemetry

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import io.opentelemetry.context.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext

class TraceCoroutineContextTest {

    @Test
    fun `traceCoroutineContext - Carry the trace of the caller and nothing else of its propagated context`() {
        val trace = OpenTelemetryPropagationContext(Context.root())
        val callerContext = PropagatedContext.empty().plus(trace).plus(TransactionElement)

        val carried = callerContext.propagate().use { traceCoroutineContext() }
            .let(KotlinCoroutinePropagation::findPropagatedContext)

        assertEquals(listOf(trace), carried?.allElements)
    }

    @Test
    fun `traceCoroutineContext - Carry nothing where the caller has no trace`() {
        val callerContext = PropagatedContext.empty().plus(TransactionElement)

        val carried = callerContext.propagate().use { traceCoroutineContext() }

        assertSame(EmptyCoroutineContext, carried)
    }

    /**
     * Stands for the connection and the transaction of the caller, which the propagated context also holds.
     */
    private object TransactionElement : PropagatedContextElement
}
