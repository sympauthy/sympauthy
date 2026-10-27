package com.sympauthy.monitoring.telemetry

import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Exports spans through [delegate], reporting each export to [Condition.Collector] with the spans a failed
 * one lost.
 */
class CollectorReportingSpanExporter(
    private val delegate: SpanExporter,
    private val conditionMonitor: ConditionMonitor
) : SpanExporter by delegate {

    override fun export(spans: Collection<SpanData>): CompletableResultCode =
        reportExport(conditionMonitor, mapOf(Condition.Collector.SPAN to spans.size.toLong())) {
            delegate.export(spans)
        }

    override fun toString(): String = "CollectorReportingSpanExporter{$delegate}"
}

/**
 * Exports log records through [delegate], reporting each export to [Condition.Collector] with the log records
 * a failed one lost.
 */
class CollectorReportingLogRecordExporter(
    private val delegate: LogRecordExporter,
    private val conditionMonitor: ConditionMonitor
) : LogRecordExporter by delegate {

    override fun export(logs: Collection<LogRecordData>): CompletableResultCode =
        reportExport(conditionMonitor, mapOf(Condition.Collector.LOG_RECORD to logs.size.toLong())) {
            delegate.export(logs)
        }

    override fun toString(): String = "CollectorReportingLogRecordExporter{$delegate}"
}

/**
 * Exports metrics through [delegate], reporting each export to [Condition.Collector].
 *
 * A failed export loses nothing worth counting: every metric is exported as its running total, so the next
 * export that succeeds carries what this one did not.
 */
class CollectorReportingMetricExporter(
    private val delegate: MetricExporter,
    private val conditionMonitor: ConditionMonitor
) : MetricExporter by delegate {

    override fun export(metrics: Collection<MetricData>): CompletableResultCode =
        reportExport(conditionMonitor, emptyMap()) { delegate.export(metrics) }

    override fun toString(): String = "CollectorReportingMetricExporter{$delegate}"
}

/**
 * Run the [export], and report its outcome to [Condition.Collector] once it completes: a success, or a failure
 * that [lost] what it counts. An export that throws is reported as a failure, and rethrown.
 *
 * Every failure is the collector's, whatever it answered: the request is composed by this server alone, so a
 * refusal says the collector, or what this server was configured to reach it with, is wrong.
 */
private fun reportExport(
    conditionMonitor: ConditionMonitor,
    lost: Map<String, Long>,
    export: () -> CompletableResultCode
): CompletableResultCode {
    val result = try {
        export()
    } catch (failure: RuntimeException) {
        conditionMonitor.reportFailures(Condition.Collector, 1, failure, lost = lost)
        throw failure
    }
    result.whenComplete {
        if (result.isSuccess) {
            conditionMonitor.reportSuccess(Condition.Collector)
        } else {
            conditionMonitor.reportFailures(Condition.Collector, 1, result.failureThrowable, lost = lost)
        }
    }
    return result
}
