package com.sympauthy.health

import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.ConditionMonitor
import io.micronaut.health.HealthStatus
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ConditionsHealthIndicatorTest {

    private val conditionMonitor = ConditionMonitor()

    private val indicator = ConditionsHealthIndicator(conditionMonitor)

    private val since = Instant.parse("2026-10-26T12:00:00Z")

    @Test
    fun `getResult - Is up and lists nothing while every condition is operational`() = runTest {
        val result = indicator.result.awaitSingle()

        assertEquals(HealthStatus.UP, result.status)
        assertNull(result.details)
    }

    @Test
    fun `getResult - Is degraded and lists each degraded condition by name, never with its failure`() = runTest {
        conditionMonitor.reportFailure(Condition.Database, IllegalStateException("db.internal:5432"), since)
        conditionMonitor.reportFailure(Condition.Provider("google"), null, since)
        conditionMonitor.reportFailure(Condition.AuthorizationWebhook("google"), null, since)

        val result = indicator.result.awaitSingle()

        assertEquals(ConditionsHealthIndicator.DEGRADED, result.status)
        val entry = mapOf("degraded-since" to "2026-10-26T12:00:00Z")
        assertEquals(
            mapOf(
                "database" to entry,
                "providers" to mapOf("google" to entry),
                "authorization-webhooks" to mapOf("google" to entry)
            ),
            result.details
        )
    }

    @Test
    fun `DEGRADED - Ranks above UP and UNKNOWN and below DOWN`() {
        val statuses =
            listOf(HealthStatus.DOWN, ConditionsHealthIndicator.DEGRADED, HealthStatus.UNKNOWN, HealthStatus.UP)

        assertEquals(
            listOf(HealthStatus.UP, HealthStatus.UNKNOWN, ConditionsHealthIndicator.DEGRADED, HealthStatus.DOWN),
            statuses.sorted()
        )
    }
}
