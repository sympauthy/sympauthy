package com.sympauthy.util

import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The context a coroutine started by a bridge to a reactive type is handed, carrying the propagated context of the
 * code that started it: the request's trace, which what the coroutine queries is traced and what it logs is stamped
 * under.
 */
fun propagatedCoroutineContext(): CoroutineContext =
    KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, PropagatedContext.getOrEmpty())
