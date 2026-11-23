package com.sympauthy.platform.monitoring

import com.sympauthy.platform.monitoring.manager.ConditionMonitor
import com.sympauthy.platform.monitoring.manager.DegradedCondition
import com.sympauthy.platform.monitoring.model.Condition
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.health.HealthStatus
import io.micronaut.health.HealthStatus.UP
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

/**
 * Publishes the conditions this instance observes degraded, each by name with the moment it degraded, and
 * never the failure itself. A degraded condition makes `/health` [DEGRADED], never `DOWN`; why, and what
 * readiness and liveness answer, is `docs/design/logging.md`.
 */
@Singleton
class ConditionsHealthIndicator(
    @Inject private val conditionMonitor: ConditionMonitor
) : HealthIndicator {

    override fun getResult(): Publisher<HealthResult> {
        val degraded = conditionMonitor.listDegraded()
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
