package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.inject.Singleton

/**
 * The attempts to get into the server, the validation codes checked, and the passwords hashed.
 *
 * A sign-up is an attempt like a sign-in, under its own `kind`, so that one metric shows the rate of every
 * attempt. The refusals of a password sign-in are one code — an unknown identifier, a disabled account and a
 * wrong password are answered alike — and are counted together.
 */
@Singleton
class AuthenticationMetrics(
    openTelemetry: OpenTelemetry
) {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    private val attempts = meter.counterBuilder(AUTHENTICATION_ATTEMPTS)
        .setDescription("Attempts to sign in, to sign up and to pass a second factor, and how each ended.")
        .setUnit("{attempt}")
        .build()

    private val validationCodeChecks = meter.counterBuilder(VALIDATION_CODE_CHECKS)
        .setDescription("Validation codes checked, and how each check ended.")
        .setUnit("{check}")
        .build()

    private val hashDuration = meter.histogramBuilder(PASSWORD_HASH_DURATION)
        .setDescription("Duration of each password hash, which is the CPU a sign-in costs.")
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(HASH_BUCKETS)
        .build()

    private val hashActive = meter.upDownCounterBuilder(PASSWORD_HASH_ACTIVE)
        .setDescription("Password hashes computing right now.")
        .setUnit("{hash}")
        .build()

    /**
     * Run [block] as an attempt of [kind] with [method] — through the provider [providerId] names, where the
     * method is one — and record how it ended: a success if it returns, of the kind [successKind] says of what
     * it returned, and what [judge] says of what it throws otherwise. What [block] throws is rethrown.
     */
    suspend fun <T> recordAttempt(
        method: String,
        providerId: String?,
        kind: String,
        judge: (Throwable) -> Outcome,
        successKind: (T) -> String = { kind },
        block: suspend () -> T
    ): T = measureOutcome(
        onSuccess = { _, result -> recordAttempt(method, providerId, successKind(result), Outcome.Success) },
        onFailure = { _, failure -> recordAttempt(method, providerId, kind, judge(failure)) }
    ) { block() }

    private fun recordAttempt(method: String, providerId: String?, kind: String, outcome: Outcome) {
        attempts.add(
            1,
            Attributes.builder()
                .put(METHOD, method)
                .putIfNotNull(PROVIDER_ID, providerId)
                .put(KIND, kind)
                .putOutcome(outcome)
                .build()
        )
    }

    /**
     * Run [block] as a check of a validation code sent by [media], and record how it ended: a success if it
     * returns, and what [judge] says of what it throws otherwise. What [block] throws is rethrown.
     */
    suspend fun <T> recordValidationCodeCheck(media: String, judge: (Throwable) -> Outcome, block: suspend () -> T): T =
        measureOutcome(
            onSuccess = { _, _ -> recordValidationCodeCheck(media, Outcome.Success) },
            onFailure = { _, failure -> recordValidationCodeCheck(media, judge(failure)) }
        ) { block() }

    private fun recordValidationCodeCheck(media: String, outcome: Outcome) {
        validationCodeChecks.add(1, Attributes.builder().put(MEDIA, media).putOutcome(outcome).build())
    }

    /**
     * Run [block] as a password hash for [operation], counting it while it computes and recording how long it
     * took.
     */
    suspend fun <T> measureHash(operation: String, block: suspend () -> T): T {
        val attributes = Attributes.of(OPERATION, operation)
        val start = System.nanoTime()
        hashActive.add(1)
        try {
            return block()
        } finally {
            hashActive.add(-1)
            hashDuration.record((System.nanoTime() - start).nanosToSeconds(), attributes)
        }
    }

    companion object {
        const val AUTHENTICATION_ATTEMPTS = "sympauthy.authentication.attempts"
        const val VALIDATION_CODE_CHECKS = "sympauthy.validation_code.checks"
        const val PASSWORD_HASH_DURATION = "sympauthy.password.hash.duration"
        const val PASSWORD_HASH_ACTIVE = "sympauthy.password.hash.active"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.authentication"

        /** A password, typed by the person. */
        const val PASSWORD = "password"

        /** An identity asserted by a third-party provider. */
        const val PROVIDER = "provider"

        /** A one-time code from an authenticator app. */
        const val TOTP = "totp"

        /** Proving who one is, to an account that exists. */
        const val SIGN_IN = "sign_in"

        /** Creating an account. */
        const val SIGN_UP = "sign_up"

        /** Passing the second factor an account has enrolled. */
        const val SECOND_FACTOR = "second_factor"

        /** Enrolling a second factor, proven by its first code. */
        const val ENROLLMENT = "enrollment"

        private val METHOD = AttributeKey.stringKey("method")
        private val PROVIDER_ID = AttributeKey.stringKey("provider_id")
        private val KIND = AttributeKey.stringKey("kind")
        private val MEDIA = AttributeKey.stringKey("media")
        private val OPERATION = AttributeKey.stringKey("operation")

        /**
         * Around the cost a deployment configures scrypt with, from a cheap one to a host saturated by guesses.
         */
        private val HASH_BUCKETS = listOf(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0)
    }
}
