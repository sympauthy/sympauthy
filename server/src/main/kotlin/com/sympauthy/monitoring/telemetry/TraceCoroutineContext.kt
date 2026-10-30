package com.sympauthy.monitoring.telemetry

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The context a coroutine started by a bridge to a reactive type is handed: the trace of the code that started it,
 * which what the coroutine queries is traced and what it logs is stamped under. Empty where there is none.
 *
 * The trace alone, and not the rest of the propagated context: that also holds the connection and the transaction of
 * the code that started it, which the coroutine would otherwise join, and may outlive.
 */
fun traceCoroutineContext(): CoroutineContext {
    val trace = PropagatedContext.getOrEmpty()
        .find(OpenTelemetryPropagationContext::class.java)
        .orElse(null)
        ?: return EmptyCoroutineContext
    return KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, PropagatedContext.empty().plus(trace))
}
