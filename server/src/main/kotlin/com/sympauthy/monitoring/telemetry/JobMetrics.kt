package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.inject.Singleton

/**
 * How each run of a scheduled job ended, and what the jobs that clean a table deleted.
 *
 * `job` is the name of the job, from the closed set the jobs of this server define, and `kind` the singular of
 * what a deletion counts.
 */
@Singleton
class JobMetrics(
    openTelemetry: OpenTelemetry
) {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    private val runDuration = meter.histogramBuilder(JOB_RUN_DURATION)
        .setDescription("Duration of each run of a scheduled job, and how it ended.")
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(RUN_BUCKETS)
        .build()

    private val deletions = meter.counterBuilder(JOB_DELETIONS)
        .setDescription("Rows a cleaning job deleted.")
        .setUnit("{row}")
        .build()

    /**
     * Record a run of [job] that took [elapsedNanos] and ended as [outcome].
     */
    fun recordRun(job: String, elapsedNanos: Long, outcome: JobRunOutcome) {
        runDuration.record(elapsedNanos.nanosToSeconds(), Attributes.of(JOB, job, OUTCOME, outcome.value))
    }

    /**
     * Record that a run of [job] deleted [count] rows of [kind].
     */
    fun recordDeletions(job: String, kind: String, count: Long) {
        if (count > 0) {
            deletions.add(count, Attributes.of(JOB, job, KIND, kind))
        }
    }

    /**
     * How a run of a job ended. [SKIPPED] is a run another instance held the job's lease for, so that a job no
     * instance gets to run shows as runs skipped and none succeeding.
     */
    enum class JobRunOutcome(internal val value: String) {
        SUCCESS("success"),
        ERROR("error"),
        SKIPPED("skipped")
    }

    companion object {
        const val JOB_RUN_DURATION = "sympauthy.job.run.duration"
        const val JOB_DELETIONS = "sympauthy.job.deletions"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.job"

        private val JOB = AttributeKey.stringKey("job")
        private val KIND = AttributeKey.stringKey("kind")

        /**
         * From a run that finds nothing to delete to a sweep of a table left to grow.
         */
        private val RUN_BUCKETS = listOf(0.01, 0.05, 0.1, 0.5, 1.0, 5.0, 10.0, 30.0, 60.0, 300.0, 900.0)
    }
}
