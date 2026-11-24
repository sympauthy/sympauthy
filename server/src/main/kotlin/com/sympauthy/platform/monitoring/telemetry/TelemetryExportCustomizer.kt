package com.sympauthy.platform.monitoring.telemetry

import com.sympauthy.platform.monitoring.manager.ConditionMonitor
import com.sympauthy.platform.monitoring.model.Condition
import io.micronaut.context.annotation.Requires
import io.micronaut.tracing.opentelemetry.OpenTelemetryBuilderCustomizer
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Reports every export the SDK is configured with to [Condition.Collector], as `docs/design/telemetry.md` says.
 *
 * The SDK's own report of a failed export is silenced in `logback.xml`, since the condition states the outage
 * once.
 */
@Singleton
@Requires(property = TELEMETRY_ENABLED, notEquals = "false")
class TelemetryExportCustomizer(
    @Inject private val conditionMonitor: ConditionMonitor
) : OpenTelemetryBuilderCustomizer {

    override fun configure(builder: AutoConfiguredOpenTelemetrySdkBuilder) {
        val report = CollectorReport(conditionMonitor)
        builder.addSpanExporterCustomizer { exporter, _ -> CollectorReportingSpanExporter(exporter, report) }
        builder.addLogRecordExporterCustomizer { exporter, _ -> CollectorReportingLogRecordExporter(exporter, report) }
        builder.addMetricExporterCustomizer { exporter, _ -> CollectorReportingMetricExporter(exporter, report) }
    }
}
