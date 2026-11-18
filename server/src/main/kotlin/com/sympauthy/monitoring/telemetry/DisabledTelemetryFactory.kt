package com.sympauthy.monitoring.telemetry

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.opentelemetry.api.OpenTelemetry
import jakarta.inject.Singleton

/**
 * The `OpenTelemetry` the server's instruments are built on while telemetry is off: OpenTelemetry's own no-op,
 * which records into nothing and starts no thread.
 *
 * The tracing module provides one only while telemetry is on. `docs/design/telemetry.md` says why the
 * instruments need one either way.
 */
@Factory
@Requires(property = TELEMETRY_ENABLED, value = "false")
class DisabledTelemetryFactory {

    @Singleton
    fun openTelemetry(): OpenTelemetry = OpenTelemetry.noop()
}
