package com.sympauthy.monitoring

import com.sympauthy.monitoring.RemoteCallMonitor.Companion.REMOTE_CALL_DURATION
import com.sympauthy.monitoring.telemetry.InMemoryMetrics
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.attributes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.time.Instant

class RemoteCallMonitorTest {

    private val metrics = InMemoryMetrics()

    private val conditionMonitor = ConditionMonitor()

    private val remoteCallMonitor = RemoteCallMonitor(conditionMonitor, metrics.openTelemetry)

    @Test
    fun `observe - Reports a success when the block returns`() = runTest {
        conditionMonitor.reportFailure(Condition.Provider("google"))

        val result = remoteCallMonitor.observe(Condition.Provider("google"), "token", { Outcome.Success }) { "answer" }

        assertEquals("answer", result)
        // The success started the quiet period, which a failure would have reset.
        conditionMonitor.endQuietPeriods(Instant.now().plus(ConditionMonitor.QUIET_PERIOD))
        assertFalse(conditionMonitor.isDegraded(Condition.Provider("google")))
    }

    @Test
    fun `observe - Reports a failure the judge says is an error, and rethrows it`() = runTest {
        val failure = IOException("Connection refused")

        val thrown = assertThrows<IOException> {
            remoteCallMonitor.observe(Condition.Provider("google"), "token", { Outcome.Error("connection") }) {
                throw failure
            }
        }

        assertSame(failure, thrown)
        assertTrue(conditionMonitor.isDegraded(Condition.Provider("google")))
    }

    @Test
    fun `observe - Counts a failure the judge says was refused as the condition's answer`() = runTest {
        conditionMonitor.reportFailure(Condition.Provider("google"))

        assertThrows<IllegalStateException> {
            remoteCallMonitor.observe(Condition.Provider("google"), "token", { Outcome.Refused("400") }) {
                error("invalid_grant")
            }
        }

        // The refusal started the quiet period, which a failure would have reset.
        conditionMonitor.endQuietPeriods(Instant.now().plus(ConditionMonitor.QUIET_PERIOD))
        assertFalse(conditionMonitor.isDegraded(Condition.Provider("google")))
    }

    @Test
    fun `reportCall - Times the call under the condition it was made to, with how it ended`() {
        remoteCallMonitor.reportCall(Condition.AuthorizationWebhook("app"), "authorize", 1_000, Outcome.Error("401"))

        assertTrue(conditionMonitor.isDegraded(Condition.AuthorizationWebhook("app")))
        assertEquals(
            1,
            metrics.count(
                REMOTE_CALL_DURATION,
                attributes(
                    "condition" to "webhook:app",
                    "operation" to "authorize",
                    "outcome" to "error",
                    "error.type" to "401"
                )
            )
        )
    }
}
