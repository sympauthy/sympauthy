package com.sympauthy.monitoring.telemetry

import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class CollectorReportingExportersTest {

    private val conditionMonitor = mockk<ConditionMonitor>(relaxed = true)

    private val spans = List(3) { mockk<SpanData>() }

    @Test
    fun `export - Reports a span export the collector accepted as its success`() {
        val delegate = mockk<SpanExporter> { every { export(spans) } returns CompletableResultCode.ofSuccess() }

        CollectorReportingSpanExporter(delegate, conditionMonitor).export(spans)

        verify { conditionMonitor.reportSuccess(Condition.Collector, any()) }
    }

    @Test
    fun `export - Reports a failed span export with the spans it lost, once it completes`() {
        val failure = IOException("Connection refused")
        val result = CompletableResultCode()
        val delegate = mockk<SpanExporter> { every { export(spans) } returns result }

        assertSame(result, CollectorReportingSpanExporter(delegate, conditionMonitor).export(spans))
        verify(exactly = 0) { conditionMonitor.reportFailures(any(), any(), any(), any(), any()) }

        result.failExceptionally(failure)
        verify {
            conditionMonitor.reportFailures(
                Condition.Collector, 1, failure, any(), mapOf(Condition.Collector.SPAN to 3L)
            )
        }
    }

    @Test
    fun `export - Reports a failed log record export with the log records it lost`() {
        val logs = List(2) { mockk<LogRecordData>() }
        val delegate = mockk<LogRecordExporter> { every { export(logs) } returns CompletableResultCode.ofFailure() }

        CollectorReportingLogRecordExporter(delegate, conditionMonitor).export(logs)

        verify {
            conditionMonitor.reportFailures(
                Condition.Collector, 1, null, any(), mapOf(Condition.Collector.LOG_RECORD to 2L)
            )
        }
    }

    @Test
    fun `export - Reports a failed metric export as losing nothing`() {
        val delegate = mockk<MetricExporter> { every { export(any()) } returns CompletableResultCode.ofFailure() }

        CollectorReportingMetricExporter(delegate, conditionMonitor).export(emptyList())

        verify { conditionMonitor.reportFailures(Condition.Collector, 1, null, any(), emptyMap()) }
    }

    @Test
    fun `export - Reports an export that throws as a failure, and rethrows it`() {
        val failure = IllegalStateException("No HttpSenderProvider found on classpath")
        val delegate = mockk<SpanExporter> { every { export(spans) } throws failure }

        val thrown = assertThrows<IllegalStateException> {
            CollectorReportingSpanExporter(delegate, conditionMonitor).export(spans)
        }

        assertSame(failure, thrown)
        verify {
            conditionMonitor.reportFailures(
                Condition.Collector, 1, failure, any(), mapOf(Condition.Collector.SPAN to 3L)
            )
        }
    }
}
