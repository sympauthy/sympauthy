package com.sympauthy.monitoring.telemetry

import ch.qos.logback.classic.LoggerContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import jakarta.inject.Singleton
import org.slf4j.Logger.ROOT_LOGGER_NAME
import org.slf4j.LoggerFactory

/**
 * The property turning the telemetry integration on, read with the framework's own condition: on unless it is
 * `false`, which `application.yml` ships.
 */
const val TELEMETRY_ENABLED = "micronaut.otel.enabled"

/**
 * Detaches every OpenTelemetry appender from the root logger when telemetry is off.
 *
 * `logback.xml` names the appender before the configuration can say whether it is wanted, and with no SDK
 * installed into it, it would hold the records it is handed until its queue of a thousand fills, then say so on
 * stderr.
 *
 * It runs at startup, where the framework would have installed the SDK, because the framework reloads
 * `logback.xml` while the context is built and attaches a new appender.
 */
@Singleton
@Requires(property = TELEMETRY_ENABLED, value = "false")
class TelemetryLogAppenderRemover : ApplicationEventListener<StartupEvent> {

    override fun onApplicationEvent(event: StartupEvent) {
        val root = (LoggerFactory.getILoggerFactory() as LoggerContext).getLogger(ROOT_LOGGER_NAME)
        root.iteratorForAppenders().asSequence()
            .filterIsInstance<OpenTelemetryAppender>()
            .toList()
            .forEach { appender ->
                root.detachAppender(appender)
                appender.stop()
            }
    }
}
