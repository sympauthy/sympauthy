package com.sympauthy.health

import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.Conditions
import com.sympauthy.monitoring.DegradedCondition
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.health.HealthStatus
import io.micronaut.health.HealthStatus.UP
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

/**
 * Publishes the conditions this instance observes degraded, each by name with the moment it degraded.
 *
 * A degraded condition makes `/health` [DEGRADED], which is answered with a `200`, and it is published on
 * neither readiness nor liveness: an external dependency fails for every instance at once, and pulling them
 * all out of the load balancer would make one broken dependency an outage.
 *
 * The failure itself is never published. The details are visible anonymously, and an exception carries
 * hostnames, internal addresses and response bodies; it is in the ERROR line, for whoever reads the console.
 */
@Singleton
class ConditionsHealthIndicator(
    @Inject private val conditions: Conditions
) : HealthIndicator {

    override fun getResult(): Publisher<HealthResult> {
        val degraded = conditions.listDegraded()
        val builder = HealthResult.builder(HEALTH_INDICATOR_NAME)
        if (degraded.isEmpty()) {
            builder.status(UP)
        } else {
            builder.status(DEGRADED)
            builder.details(details(degraded))
        }
        return Publishers.just(builder.build())
    }

    /**
     * The [degraded] conditions, a provider under `providers` and a client's authorization webhook under
     * `authorization-webhooks`, since a provider and a client may share an id.
     */
    internal fun details(degraded: List<DegradedCondition>): Map<String, Any> {
        val details = sortedMapOf<String, Any>()
        val providers = sortedMapOf<String, Any>()
        val authorizationWebhooks = sortedMapOf<String, Any>()
        degraded.forEach { (condition, since) ->
            val entry = mapOf("degraded-since" to since.toString())
            when (condition) {
                is Condition.Provider -> providers[condition.name] = entry
                is Condition.AuthorizationWebhook -> authorizationWebhooks[condition.name] = entry
                else -> details[condition.name] = entry
            }
        }
        if (providers.isNotEmpty()) details["providers"] = providers
        if (authorizationWebhooks.isNotEmpty()) details["authorization-webhooks"] = authorizationWebhooks
        return details
    }

    companion object {
        private const val HEALTH_INDICATOR_NAME = "conditions"

        /**
         * Partially operational: neither operational nor not, which the health endpoint answers with a `200`,
         * and ranked above `UNKNOWN` so that it is what an aggregate of the two reports.
         */
        val DEGRADED = HealthStatus("DEGRADED", "A dependency is degraded.", null, 500)
    }
}
