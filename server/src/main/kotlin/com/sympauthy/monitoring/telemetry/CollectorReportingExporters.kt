package com.sympauthy.monitoring.telemetry

import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.common.export.MemoryMode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.Aggregation
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Reports the exports of every signal to [Condition.Collector], which recovers only once every signal whose
 * last export failed has exported again.
 *
 * One signal succeeding says nothing of another: a deployment may send each to an endpoint of its own, and
 * metrics are exported every few seconds while a span is exported only once a request is served. Were any
 * success to start the quiet period, the metrics would recover the condition in every lull in traffic while
 * every span export still failed.
 */
class CollectorReport(
    private val conditionMonitor: ConditionMonitor
) {

    private val failing = mutableSetOf<Signal>()

    /**
     * Run the [export] of [signal], and report its outcome once it completes: a success, or a failure that
     * [lost] what it counts. An export that throws is reported as a failure, and rethrown.
     *
     * Every failure is the collector's, whatever it answered: the request is composed by this server alone, so
     * a refusal says the collector, or what this server was configured to reach it with, is wrong.
     */
    internal fun export(
        signal: Signal,
        lost: Map<String, Long>,
        export: () -> CompletableResultCode
    ): CompletableResultCode {
        val result = try {
            export()
        } catch (failure: Throwable) {
            // An Error included: a sender missing from the native image throws one on every export.
            reportFailure(signal, failure, lost)
            throw failure
        }
        result.whenComplete {
            if (result.isSuccess) {
                reportSuccess(signal)
            } else {
                reportFailure(signal, result.failureThrowable, lost)
            }
        }
        return result
    }

    /**
     * Held across the report so that a success read as the last one cannot start the quiet period after a
     * failure of another signal was reported.
     */
    @Synchronized
    private fun reportFailure(signal: Signal, failure: Throwable?, lost: Map<String, Long>) {
        failing += signal
        conditionMonitor.reportFailures(Condition.Collector, 1, failure, lost = lost)
    }

    @Synchronized
    private fun reportSuccess(signal: Signal) {
        failing -= signal
        if (failing.isEmpty()) {
            conditionMonitor.reportSuccess(Condition.Collector)
        }
    }

    internal enum class Signal { TRACES, METRICS, LOGS }
}

/**
 * Exports spans through [delegate], reporting each export to [CollectorReport] with the spans a failed
 * one lost.
 */
class CollectorReportingSpanExporter(
    private val delegate: SpanExporter,
    private val report: CollectorReport
) : SpanExporter by delegate {

    override fun export(spans: Collection<SpanData>): CompletableResultCode =
        report.export(CollectorReport.Signal.TRACES, mapOf(Condition.Collector.SPAN to spans.size.toLong())) {
            delegate.export(spans)
        }

    override fun toString(): String = "CollectorReportingSpanExporter{$delegate}"
}

/**
 * Exports log records through [delegate], reporting each export to [CollectorReport] with the log records
 * a failed one lost.
 */
class CollectorReportingLogRecordExporter(
    private val delegate: LogRecordExporter,
    private val report: CollectorReport
) : LogRecordExporter by delegate {

    override fun export(logs: Collection<LogRecordData>): CompletableResultCode =
        report.export(CollectorReport.Signal.LOGS, mapOf(Condition.Collector.LOG_RECORD to logs.size.toLong())) {
            delegate.export(logs)
        }

    override fun toString(): String = "CollectorReportingLogRecordExporter{$delegate}"
}

/**
 * Exports metrics through [delegate], reporting each export to [CollectorReport].
 *
 * A failed export loses nothing worth counting: every metric is exported as its running total, so the next
 * export that succeeds carries what this one did not.
 *
 * The defaults the reader asks its exporter for are forwarded by hand: delegation leaves out a member Java
 * declares with a body, and the interface's would replace what a deployment configured the exporter with.
 */
class CollectorReportingMetricExporter(
    private val delegate: MetricExporter,
    private val report: CollectorReport
) : MetricExporter by delegate {

    override fun export(metrics: Collection<MetricData>): CompletableResultCode =
        report.export(CollectorReport.Signal.METRICS, emptyMap()) { delegate.export(metrics) }

    override fun getDefaultAggregation(instrumentType: InstrumentType): Aggregation =
        delegate.getDefaultAggregation(instrumentType)

    override fun getMemoryMode(): MemoryMode = delegate.getMemoryMode()

    override fun toString(): String = "CollectorReportingMetricExporter{$delegate}"
}
