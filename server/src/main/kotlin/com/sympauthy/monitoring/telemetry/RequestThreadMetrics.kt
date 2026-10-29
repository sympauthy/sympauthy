package com.sympauthy.monitoring.telemetry

import io.micronaut.context.annotation.Requires
import io.micronaut.http.bind.binders.HttpCoroutineContextFactory
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

/**
 * How many threads of the pool `suspend` routes resume on are running a request's work right now, under the name
 * Micrometer gives an executor's active threads.
 *
 * A route naming no executor resumes on Kotlin's default dispatcher, which counts nothing it runs. This hands every
 * route [CountingDispatcher] in its place, which runs every task on that same dispatcher and counts the ones
 * running. Only telemetry turned on puts it in front: off, a route runs on the dispatcher it always did.
 */
@Singleton
@Requires(property = TELEMETRY_ENABLED, notEquals = "false")
class RequestThreadMetrics(
    openTelemetry: OpenTelemetry
) : HttpCoroutineContextFactory<CoroutineDispatcher> {

    private val dispatcher = CountingDispatcher(Dispatchers.Default)

    private val instrument = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
        .upDownCounterBuilder(EXECUTOR_ACTIVE)
        .setDescription("The approximate number of threads that are actively executing tasks.")
        .setUnit("{thread}")
        .buildWithCallback { it.record(dispatcher.running, Attributes.of(NAME, REQUEST)) }

    override fun create(): CoroutineDispatcher = dispatcher

    @PreDestroy
    fun close() {
        instrument.close()
    }

    companion object {
        const val EXECUTOR_ACTIVE = "executor.active"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.request"

        /**
         * The name the pool `suspend` routes resume on is recorded under.
         */
        private const val REQUEST = "request"

        private val NAME = AttributeKey.stringKey("name")
    }
}

/**
 * Runs every task on [delegate], counting the ones running.
 */
class CountingDispatcher(
    private val delegate: CoroutineDispatcher
) : CoroutineDispatcher() {

    private val runningCount = AtomicLong()

    /**
     * The tasks running now.
     */
    val running: Long get() = runningCount.get()

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = delegate.isDispatchNeeded(context)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        delegate.dispatch(context) {
            runningCount.incrementAndGet()
            try {
                block.run()
            } finally {
                runningCount.decrementAndGet()
            }
        }
    }

    override fun toString(): String = "CountingDispatcher{$delegate}"
}
