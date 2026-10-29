package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.CollectionRegistration
import io.opentelemetry.sdk.metrics.export.MetricReader

/**
 * An SDK whose metrics a test reads on demand, as the exporter would at its next export.
 */
class InMemoryMetrics : MetricReader {

    private lateinit var registration: CollectionRegistration

    val openTelemetry: OpenTelemetry = OpenTelemetrySdk.builder()
        .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(this).build())
        .build()

    override fun register(registration: CollectionRegistration) {
        this.registration = registration
    }

    override fun getAggregationTemporality(instrumentType: InstrumentType) = AggregationTemporality.CUMULATIVE

    override fun forceFlush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

    /**
     * The metric named [name], as it would be exported now.
     */
    fun metric(name: String): MetricData = registration.collectAllMetrics().single { it.name == name }

    /**
     * The attributes of every point the metric named [name] holds now.
     */
    fun points(name: String): List<Attributes> = metric(name).data.points.map { it.attributes }

    /**
     * The count of the histogram named [name] under exactly [attributes].
     */
    fun count(name: String, attributes: Attributes): Long =
        metric(name).histogramData.points.single { it.attributes == attributes }.count

    /**
     * The value of the long metric named [name] under exactly [attributes].
     */
    fun value(name: String, attributes: Attributes): Long {
        val metric = metric(name)
        val points = when {
            metric.longSumData.points.isNotEmpty() -> metric.longSumData.points
            else -> metric.longGaugeData.points
        }
        return points.single { it.attributes == attributes }.value
    }
}

/**
 * [pairs] as attributes, every one a string.
 */
fun attributes(vararg pairs: Pair<String, String>): Attributes =
    pairs.fold(Attributes.builder()) { builder, (key, value) -> builder.put(AttributeKey.stringKey(key), value) }
        .build()
