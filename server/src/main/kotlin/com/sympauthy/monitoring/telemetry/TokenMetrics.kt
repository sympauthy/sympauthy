package com.sympauthy.monitoring.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import jakarta.inject.Singleton

/**
 * The token endpoint's requests, the checks of the access tokens presented to this server, and the grants
 * revoked.
 *
 * A token request is recorded by the manager issuing its tokens, once its client has authenticated, so it always
 * carries `client_id`: an id a request sent for a client that does not exist, or that it failed to authenticate
 * as, is chosen by whoever sent it.
 */
@Singleton
class TokenMetrics(
    openTelemetry: OpenTelemetry
) {

    private val meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)

    private val requestDuration = meter.histogramBuilder(TOKEN_REQUEST_DURATION)
        .setDescription("Duration of the requests to the token endpoint, and how each ended.")
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(REQUEST_BUCKETS)
        .build()

    private val validationDuration = meter.histogramBuilder(ACCESS_TOKEN_VALIDATION_DURATION)
        .setDescription("Duration of the checks of an access token presented to this server, and how each ended.")
        .setUnit("s")
        .setExplicitBucketBoundariesAdvice(VALIDATION_BUCKETS)
        .build()

    private val revocations = meter.counterBuilder(GRANT_REVOCATIONS)
        .setDescription("Grants revoked, and why.")
        .setUnit("{grant}")
        .build()

    /**
     * Record a request to the token endpoint by the client [clientId] names for [grantType], issuing tokens bound
     * under [scheme], that took [elapsedNanos] and ended as [outcome].
     */
    fun recordRequest(grantType: String, scheme: String, clientId: String, elapsedNanos: Long, outcome: Outcome) {
        requestDuration.record(
            elapsedNanos.nanosToSeconds(),
            Attributes.builder()
                .put(GRANT_TYPE, grantType)
                .put(SCHEME, scheme)
                .put(CLIENT_ID, clientId)
                .putOutcome(outcome)
                .build()
        )
    }

    /**
     * Record a check of an access token presented under [scheme], that took [elapsedNanos] and ended as
     * [outcome], for the client [clientId] names if the token was found.
     */
    fun recordValidation(scheme: String, clientId: String?, elapsedNanos: Long, outcome: Outcome) {
        validationDuration.record(
            elapsedNanos.nanosToSeconds(),
            Attributes.builder()
                .put(SCHEME, scheme)
                .putIfNotNull(CLIENT_ID, clientId)
                .putOutcome(outcome)
                .build()
        )
    }

    /**
     * Record that [count] grants opened for [clientId] were revoked by what [revokedBy] names. A null [clientId]
     * is a revocation spanning grants of several clients.
     */
    fun recordRevocations(revokedBy: String, clientId: String?, count: Int) {
        if (count > 0) {
            revocations.add(
                count.toLong(),
                Attributes.builder().put(REVOKED_BY, revokedBy).putIfNotNull(CLIENT_ID, clientId).build()
            )
        }
    }

    companion object {
        const val TOKEN_REQUEST_DURATION = "sympauthy.token.request.duration"
        const val ACCESS_TOKEN_VALIDATION_DURATION = "sympauthy.access_token.validation.duration"
        const val GRANT_REVOCATIONS = "sympauthy.grant.revocations"

        private const val INSTRUMENTATION_SCOPE = "com.sympauthy.token"

        private val GRANT_TYPE = AttributeKey.stringKey("grant_type")
        private val SCHEME = AttributeKey.stringKey("scheme")
        private val REVOKED_BY = AttributeKey.stringKey("revoked_by")

        /**
         * From a request answered at once to one waiting on a database under load.
         */
        private val REQUEST_BUCKETS = listOf(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)

        /**
         * From a token checked against a warm database to one waiting for a connection.
         */
        private val VALIDATION_BUCKETS = listOf(0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0)
    }
}
