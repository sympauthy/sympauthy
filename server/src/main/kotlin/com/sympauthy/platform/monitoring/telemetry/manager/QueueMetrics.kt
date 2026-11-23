package com.sympauthy.platform.monitoring.telemetry.manager

import com.sympauthy.platform.monitoring.model.Outcome
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton

/**
 * What waits in the queue delivering what this server promised somebody, and what it gave up, each by
 * `destination` and `kind` — see `docs/design/telemetry.md`.
 *
 * The backlog is published as the last poll read it, since a gauge's callback is no place for a query: it lags by
 * at most a poll, and an instance that does not poll publishes nothing.
 */
@Singleton
class QueueMetrics(
    openTelemetry: OpenTelemetry
) {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    @Volatile
    private var backlog: List<Backlog> = emptyList()

    private val pending = meter.gaugeBuilder(QUEUE_PENDING)
        .ofLongs()
        .setDescription("Items waiting to be delivered, across the deployment.")
        .setUnit("{item}")
        .buildWithCallback { measurement ->
            backlog.forEach { measurement.record(it.pending, it.attributes()) }
        }

    private val oldestAge = meter.gaugeBuilder(QUEUE_OLDEST_AGE)
        .ofLongs()
        .setDescription("How long the oldest item waiting to be delivered has waited, 0 when none waits.")
        .setUnit("s")
        .buildWithCallback { measurement ->
            backlog.forEach { measurement.record(it.oldestAgeSeconds, it.attributes()) }
        }

    private val givenUp = meter.counterBuilder(QUEUE_GIVEN_UP)
        .setDescription("Items the queue gave up without delivering them, and why.")
        .setUnit("{item}")
        .build()

    /**
     * Publish [backlog] in place of what the last poll read. A kind with nothing waiting is published as `0` rather
     * than left out, so that a backlog that drained reads as one.
     */
    fun recordBacklog(backlog: List<Backlog>) {
        this.backlog = backlog
    }

    /**
     * Record that an item of [kind] bound for [destination] was given up, as [outcome] says why.
     */
    fun recordGivenUp(destination: String, kind: String, outcome: Outcome) {
        givenUp.add(
            1,
            Attributes.builder().put(DESTINATION, destination).put(KIND, kind).putOutcome(outcome).build()
        )
    }

    @PreDestroy
    fun close() {
        pending.close()
        oldestAge.close()
    }

    /**
     * How many items of [kind] wait for [destination], and for how many seconds the oldest of them has.
     */
    data class Backlog(
        val destination: String,
        val kind: String,
        val pending: Long,
        val oldestAgeSeconds: Long
    ) {
        internal fun attributes(): Attributes = Attributes.of(DESTINATION, destination, KIND, kind)
    }

    companion object {
        const val QUEUE_PENDING = "sympauthy.queue.pending"
        const val QUEUE_OLDEST_AGE = "sympauthy.queue.oldest_age"
        const val QUEUE_GIVEN_UP = "sympauthy.queue.given_up"

        /**
         * The `error.type` of an item given up because its next attempt would have fallen after its expiry.
         */
        const val EXPIRED = "expired"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.queue"

        private val DESTINATION = AttributeKey.stringKey("destination")
        private val KIND = AttributeKey.stringKey("kind")
    }
}
