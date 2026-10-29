package com.sympauthy.monitoring.telemetry

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.opentelemetry.api.OpenTelemetry
import jakarta.inject.Singleton

/**
 * The `OpenTelemetry` the server's instruments are built on while telemetry is off: OpenTelemetry's own no-op,
 * which records into nothing and starts no thread.
 *
 * A metric is recorded where the event happens, by code that runs whether telemetry is on or not, so its
 * instruments need an `OpenTelemetry` either way; the tracing module provides one only when it is on.
 */
@Factory
@Requires(property = TELEMETRY_ENABLED, value = "false")
class DisabledTelemetryFactory {

    @Singleton
    fun openTelemetry(): OpenTelemetry = OpenTelemetry.noop()
}
