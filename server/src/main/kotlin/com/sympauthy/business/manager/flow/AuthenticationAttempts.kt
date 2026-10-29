package com.sympauthy.business.manager.flow

import com.sympauthy.exception.toOutcome
import com.sympauthy.monitoring.telemetry.AuthenticationMetrics
import com.sympauthy.monitoring.telemetry.Outcome
import com.sympauthy.monitoring.telemetry.measureOutcome

/**
 * Run [block] as an authentication attempt with [method], through the provider [providerId] names where the method
 * is one, recorded once it ends under the kind [block] declares through its argument: [kind] until the branch it
 * takes is known, and nothing at all where that branch declares none.
 *
 * A branch that authenticates nobody — a provider linked to an account already signed in, a step where signing in
 * no longer applies — is not an attempt, and declares no kind; a failure before any branch is known is one of
 * [kind]. What [block] throws is rethrown.
 */
internal suspend fun <T> AuthenticationMetrics.recordAttemptOfDeclaredKind(
    method: String,
    providerId: String?,
    kind: String,
    block: suspend (declareKind: (String?) -> Unit) -> T
): T {
    var declaredKind: String? = kind
    return measureOutcome(
        onSuccess = { _, _ -> declaredKind?.let { recordAttempt(method, providerId, it, Outcome.Success) } },
        onFailure = { _, failure -> declaredKind?.let { recordAttempt(method, providerId, it, failure.toOutcome()) } }
    ) { block { declaredKind = it } }
}
