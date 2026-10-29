package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.inject.Singleton

/**
 * How long a transaction waited to take the locks over the objects it is about to change.
 *
 * `lock` names what was locked by the kinds of the keys taken together — `user`, or
 * `identifier_value+provider_subject+user` for a promotion — from the closed set of kinds a lock may be taken
 * over.
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
     * Record a wait of [elapsedNanos] to take the locks over the kinds of object [lock] names.
     */
    fun recordWait(lock: String, elapsedNanos: Long) {
        waitDuration.record(elapsedNanos.nanosToSeconds(), Attributes.of(LOCK, lock))
    }

    companion object {
        const val LOCK_WAIT_DURATION = "sympauthy.lock.wait.duration"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.lock"

        private val LOCK = AttributeKey.stringKey("lock")

        /**
         * From a row nobody else holds to a transaction queued behind a slow one.
         */
        private val WAIT_BUCKETS = listOf(0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 5.0)
    }
}
