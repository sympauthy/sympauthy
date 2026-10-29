package com.sympauthy.exception

import com.sympauthy.api.exception.LocalizedHttpException
import com.sympauthy.api.exception.OAuth2Exception
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.monitoring.telemetry.Outcome

/**
 * How an event that ended in this failure is recorded on a metric: [Outcome.Refused] where the caller caused it,
 * [Outcome.Error] where the server or a dependency failed, typed by the error code the caller is answered with.
 *
 * The caller caused it where the failure is recoverable, or answers with a `4xx`; a business failure answers
 * with the status it recommends. A failure carrying no code is an error, typed by its class.
 */
fun Throwable.toOutcome(): Outcome = when (this) {
    is OAuth2Exception -> outcomeOf(status.code, detailsId)
    is LocalizedHttpException -> outcomeOf(status.code, detailsId)
    is BusinessException ->
        if (recoverable) Outcome.Refused(detailsId) else outcomeOf(recommendedStatus?.code, detailsId)
    is LocalizedException -> if (recoverable) Outcome.Refused(detailsId) else Outcome.Error(detailsId)
    else -> Outcome.errorOf(this)
}

private fun outcomeOf(status: Int?, errorType: String): Outcome =
    if (status != null && status < 500) Outcome.Refused(errorType) else Outcome.Error(errorType)
