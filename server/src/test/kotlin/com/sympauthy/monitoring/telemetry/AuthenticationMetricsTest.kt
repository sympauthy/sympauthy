package com.sympauthy.monitoring.telemetry

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthenticationMetricsTest {

    private val metrics = InMemoryMetrics()

    private val authenticationMetrics = AuthenticationMetrics(metrics.openTelemetry)

    @Test
    fun `recordAttempt - Counts a refusal under the code the judge gives, and rethrows it`() = runTest {
        val failure = IllegalStateException("flow.password.sign_in.invalid")

        val thrown = assertThrows<IllegalStateException> {
            authenticationMetrics.recordAttempt("password", null, "sign_in", { Outcome.Refused(it.message!!) }) {
                throw failure
            }
        }

        assertSame(failure, thrown)
        assertEquals(
            1,
            metrics.value(
                AuthenticationMetrics.AUTHENTICATION_ATTEMPTS,
                attributes(
                    "method" to "password",
                    "kind" to "sign_in",
                    "outcome" to "refused",
                    "error.type" to "flow.password.sign_in.invalid"
                )
            )
        )
    }

    @Test
    fun `recordAttempt - Counts a success under the kind its result says it was`() = runTest {
        authenticationMetrics.recordAttempt(
            method = "provider",
            providerId = "google",
            kind = "sign_in",
            judge = Outcome::errorOf,
            successKind = { "sign_up" }
        ) { }

        assertEquals(
            1,
            metrics.value(
                AuthenticationMetrics.AUTHENTICATION_ATTEMPTS,
                attributes(
                    "method" to "provider",
                    "provider_id" to "google",
                    "kind" to "sign_up",
                    "outcome" to "success"
                )
            )
        )
    }

    @Test
    fun `measureHash - Counts a hash while it computes and times it`() = runTest {
        val active = authenticationMetrics.measureHash("verify") {
            metrics.value(AuthenticationMetrics.PASSWORD_HASH_ACTIVE, attributes())
        }

        assertEquals(1, active)
        assertEquals(0, metrics.value(AuthenticationMetrics.PASSWORD_HASH_ACTIVE, attributes()))
        assertEquals(
            1,
            metrics.count(AuthenticationMetrics.PASSWORD_HASH_DURATION, attributes("operation" to "verify"))
        )
    }
}
