package com.sympauthy.monitoring.telemetry

import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.annotation.PreDestroy

/**
 * Publishes whether each condition [ConditionMonitor] has observed is degraded, under `condition`, which is
 * [Condition.metricName].
 *
 * `sympauthy.condition.degraded` is `1` exactly while `/health` lists the condition, whatever the condition is: a
 * dependency reached by a call or the console's own queue.
 */
@Context
@Requires(property = TELEMETRY_ENABLED, notEquals = "false")
class ConditionMetrics(
    openTelemetry: OpenTelemetry,
    private val conditionMonitor: ConditionMonitor
) {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    private val degraded = meter.gaugeBuilder(CONDITION_DEGRADED)
        .ofLongs()
        .setDescription("Whether a condition is degraded, as /health reports it: 1 when it is, 0 when it is not.")
        .setUnit("1")
        .buildWithCallback { measurement ->
            conditionMonitor.listObserved().forEach { (condition, isDegraded) ->
                measurement.record(if (isDegraded) 1 else 0, Attributes.of(CONDITION, condition.metricName))
            }
        }

    @PreDestroy
    fun close() {
        degraded.close()
    }

    companion object {
        const val CONDITION_DEGRADED = "sympauthy.condition.degraded"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.condition"

        private val CONDITION = AttributeKey.stringKey("condition")
    }
}
