package com.sympauthy.platform.monitoring.telemetry.manager

import com.sympauthy.platform.monitoring.manager.ConditionMonitor
import com.sympauthy.platform.monitoring.model.Condition
import com.sympauthy.platform.monitoring.telemetry.manager.ConditionMetrics.Companion.CONDITION_DEGRADED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConditionMetricsTest {

    private val metrics = InMemoryMetrics()

    private val conditionMonitor = ConditionMonitor()

    init {
        ConditionMetrics(metrics.openTelemetry, conditionMonitor)
    }

    @Test
    fun `degraded - Is 1 for a degraded condition and 0 for one that answered`() {
        conditionMonitor.reportFailure(Condition.Provider("google"))
        conditionMonitor.reportSuccess(Condition.Mail)

        assertEquals(1, metrics.value(CONDITION_DEGRADED, attributes("condition" to "provider:google")))
        assertEquals(0, metrics.value(CONDITION_DEGRADED, attributes("condition" to "mail")))
    }
}
