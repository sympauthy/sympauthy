package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.r2dbc.spi.R2dbcException
import jakarta.inject.Singleton

/**
 * How long a transaction waited to take the locks over the objects it is about to change, and how the wait ended.
 *
 * `lock` names what was locked by the kinds of the keys taken together — `user`, or
 * `identifier_value+provider_subject+user` for a promotion — from the closed set of kinds a lock may be taken
 * over. A wait that ended in a failure — a lock timeout, a deadlock — is recorded too, as an `error`, since it is
 * the worst contention there is.
 */
@Singleton
class LockMetrics(
    openTelemetry: OpenTelemetry
) {

    private val waitDuration = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
        .histogramBuilder(LOCK_WAIT_DURATION)
        .setDescription("Time a transaction waited to take the locks over the objects it changes.")
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(WAIT_BUCKETS)
        .build()

    /**
     * Record a wait of [elapsedNanos] to take the locks over the kinds of object [lock] names, which ended as
     * [outcome].
     */
    fun recordWait(lock: String, elapsedNanos: Long, outcome: Outcome) {
        waitDuration.record(
            elapsedNanos.nanosToSeconds(),
            Attributes.builder().put(LOCK, lock).putOutcome(outcome).build()
        )
    }

    companion object {
        const val LOCK_WAIT_DURATION = "sympauthy.lock.wait.duration"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.lock"

        private val LOCK = AttributeKey.stringKey("lock")

        /**
         * From a row nobody else holds to a transaction queued behind a slow one.
         */
        private val WAIT_BUCKETS = listOf(0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 5.0)

        /**
         * How a wait that ended in [failure] is recorded: an error typed by the SQLSTATE the database answered
         * with — `55P03` for a lock not available in time, `40P01` for a deadlock — and by the failure's class
         * where it carries none.
         */
        fun failedWait(failure: Throwable): Outcome.Error {
            val sqlState = generateSequence(failure) { it.cause }
                .filterIsInstance<R2dbcException>()
                .firstNotNullOfOrNull { it.sqlState }
            return sqlState?.let { Outcome.Error(it) } ?: Outcome.errorOf(failure)
        }
    }
}
