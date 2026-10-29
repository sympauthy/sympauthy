package com.sympauthy.monitoring

import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.nanosToSeconds
import com.sympauthy.monitoring.telemetry.putOutcome
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Every call this server makes outside its process: timed on `sympauthy.remote_call.duration`, and reported to
 * the [Condition] the call is made to.
 *
 * A remote call is always a condition's, and a condition is not always reached by a call — the console's queue
 * is this server's own — so the conditions are [ConditionMonitor]'s and this only reports to it. An
 * [Outcome.Error] is a failure of the condition; a success and an [Outcome.Refused] are both its answer, a
 * refusal being one to what was sent.
 *
 * The collector's exports are not reported here: its condition recovers only once every signal has exported
 * again, which the exporters report to [ConditionMonitor] themselves.
 */
@Singleton
class RemoteCallMonitor(
    private val conditionMonitor: ConditionMonitor,
    openTelemetry: OpenTelemetry
) {

    private val callDuration = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
        .histogramBuilder(REMOTE_CALL_DURATION)
        .setDescription("Duration of the calls this server makes outside its process.")
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(CALL_BUCKETS)
        .build()

    /**
     * Run [block] as a call made to [condition] for [operation], and report it once it ends: a success if
     * [block] returns, and what [judge] says of what it throws otherwise. What [block] throws is rethrown.
     */
    suspend fun <T> observe(
        condition: Condition,
        operation: String,
        judge: (Throwable) -> Outcome,
        block: suspend () -> T
    ): T {
        val start = System.nanoTime()
        val result = try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            reportCall(condition, operation, System.nanoTime() - start, judge(failure), failure)
            throw failure
        }
        reportCall(condition, operation, System.nanoTime() - start, Outcome.Success)
        return result
    }

    /**
     * Report a call made to [condition] for [operation], which ended with [outcome] after [elapsedNanos], with
     * the [failure] that says how if it failed, and [lost] what it counts.
     */
    fun reportCall(
        condition: Condition,
        operation: String,
        elapsedNanos: Long,
        outcome: Outcome,
        failure: Throwable? = null,
        lost: Map<String, Long> = emptyMap()
    ) {
        when (outcome) {
            Outcome.Success, is Outcome.Refused -> conditionMonitor.reportSuccess(condition)
            is Outcome.Error -> conditionMonitor.reportFailures(condition, 1, failure, lost = lost)
        }
        callDuration.record(
            elapsedNanos.nanosToSeconds(),
            Attributes.builder()
                .put(CONDITION, condition.metricName)
                .put(OPERATION, operation)
                .putOutcome(outcome)
                .build()
        )
    }

    companion object {
        const val REMOTE_CALL_DURATION = "sympauthy.remote_call.duration"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.remote_call"

        private val CONDITION = AttributeKey.stringKey("condition")
        private val OPERATION = AttributeKey.stringKey("operation")

        /**
         * From a call answered on the same network to a provider timing out.
         */
        private val CALL_BUCKETS = listOf(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0)
    }
}
