package com.sympauthy.exception

import com.sympauthy.monitoring.telemetry.Outcome

/**
 * How an event that ended in this failure is recorded on a metric: [Outcome.Refused] where the caller caused it,
 * [Outcome.Error] where the server or a dependency failed, typed by the error code the caller is answered with.
 *
 * The caller caused it where the failure is recoverable, or is answered with a `4xx`. A failure that decides no
 * status and is not recoverable is an error, and one carrying no code is an error typed by its class.
 */
fun Throwable.toOutcome(): Outcome {
    if (this !is AnsweredFailure) return Outcome.errorOf(this)
    val status = answeredStatus
    return if (recoverable || (status != null && status.code < SERVER_ERROR)) {
        Outcome.Refused(detailsId)
    } else {
        Outcome.Error(detailsId)
    }
}

/**
 * The first status a server's own failure is answered with.
 */
private const val SERVER_ERROR = 500
