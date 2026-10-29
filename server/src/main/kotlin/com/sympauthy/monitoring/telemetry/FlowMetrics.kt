package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.inject.Singleton

/**
 * The interactive flow sessions started, how long each took to end and how, and the ones that expired.
 *
 * `purpose` is the purpose a session was started for, and `client_id` the client it was started for where it
 * was started for a client the configuration declares. A session that expired has no duration of its own,
 * since it would be the expiry every time: it is counted once collected, under the first purpose it had not
 * finished, which is where its person stopped.
 */
@Singleton
class FlowMetrics(
    openTelemetry: OpenTelemetry
) {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    private val started = meter.counterBuilder(FLOW_SESSIONS_STARTED)
        .setDescription("Interactive flow sessions started, and the requests refused before one could start.")
        .setUnit("{session}")
        .build()

    private val duration = meter.histogramBuilder(FLOW_SESSION_DURATION)
        .setDescription("Time from the start of an interactive flow session to its end, and how it ended.")
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(SESSION_BUCKETS)
        .build()

    private val expired = meter.counterBuilder(FLOW_SESSIONS_EXPIRED)
        .setDescription("Interactive flow sessions collected after they expired unfinished.")
        .setUnit("{session}")
        .build()

    /**
     * Record a session started for [purpose], for the client [clientId] names, which ended its start as
     * [outcome]: a refusal is a session that was failed as it was created, or a request refused before one was.
     */
    fun recordStarted(purpose: String, clientId: String?, outcome: Outcome) {
        started.add(
            1,
            Attributes.builder()
                .put(PURPOSE, purpose)
                .putIfNotNull(CLIENT_ID, clientId)
                .putOutcome(outcome)
                .build()
        )
    }

    /**
     * Record a session started for [purpose], for the client [clientId] names, that ended as [ending] after
     * [elapsedSeconds], failed with the error code [errorType] if it failed.
     */
    fun recordEnded(
        purpose: String,
        clientId: String?,
        elapsedSeconds: Double,
        ending: SessionEnding,
        errorType: String? = null
    ) {
        duration.record(
            elapsedSeconds,
            Attributes.builder()
                .put(PURPOSE, purpose)
                .putIfNotNull(CLIENT_ID, clientId)
                .put(OUTCOME, ending.value)
                .putIfNotNull(ERROR_TYPE, errorType)
                .build()
        )
    }

    /**
     * Record a session started for [purpose], for the client [clientId] names, that expired before finishing
     * [stalledPurpose].
     */
    fun recordExpired(purpose: String, stalledPurpose: String?, clientId: String?) {
        expired.add(
            1,
            Attributes.builder()
                .put(PURPOSE, purpose)
                .putIfNotNull(STALLED_PURPOSE, stalledPurpose)
                .putIfNotNull(CLIENT_ID, clientId)
                .build()
        )
    }

    /**
     * How a session ended, other than by expiring.
     */
    enum class SessionEnding(internal val value: String) {
        COMPLETED("completed"),
        FAILED("failed"),
        CANCELLED("cancelled")
    }

    companion object {
        const val FLOW_SESSIONS_STARTED = "sympauthy.flow.sessions.started"
        const val FLOW_SESSION_DURATION = "sympauthy.flow.session.duration"
        const val FLOW_SESSIONS_EXPIRED = "sympauthy.flow.sessions.expired"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.flow"

        private val PURPOSE = AttributeKey.stringKey("purpose")
        private val STALLED_PURPOSE = AttributeKey.stringKey("stalled_purpose")

        /**
         * From a person whose sign-in the browser completed alone to one reading their mail for a code.
         */
        private val SESSION_BUCKETS = listOf(1.0, 2.5, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0, 600.0, 1800.0, 3600.0)
    }
}
