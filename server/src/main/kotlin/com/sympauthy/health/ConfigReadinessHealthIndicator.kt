package com.sympauthy.health

import com.sympauthy.config.ConfigReadiness
import com.sympauthy.server.ErrorMessages
import com.sympauthy.util.getKeyAndLocalizedMessage
import io.micronaut.context.MessageSource
import io.micronaut.health.HealthStatus.DOWN
import io.micronaut.health.HealthStatus.UP
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.management.health.indicator.annotation.Readiness
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher

/**
 * Publishes whether the deployment's configuration is usable.
 *
 * It lives apart from the configuration it reports on, rather than beside it, because the dependency
 * only runs one way: what is being reported on may not reach for the thing reporting it.
 */
@Singleton
@Readiness
open class ConfigReadinessHealthIndicator(
    @Inject private val configReadiness: ConfigReadiness,
    @Inject @param:ErrorMessages private val messageSource: MessageSource,
) : HealthIndicator {

    override fun getResult(): Publisher<HealthResult> {
        return flow {
            val configurationErrors = configReadiness.getConfigurationErrors()
            val builder = HealthResult.builder(HEALTH_INDICATOR_NAME)
            if (configurationErrors.isEmpty()) {
                builder.status(UP)
            } else {
                builder.status(DOWN)
                builder.details(details(configurationErrors))
            }
            emit(builder.build())
        }.asPublisher()
    }

    /**
     * The errors under the key each of them names, one entry per key rather than one per error: two
     * files naming the same key that binds to nothing are two errors, and an operator who is told about
     * one of them fixes it and finds readiness still down.
     */
    private fun details(configurationErrors: List<Exception>): Map<String, String> = configurationErrors
        .map { it.getKeyAndLocalizedMessage(messageSource) }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, messages) -> messages.filterNotNull().joinToString(" ") }

    companion object {
        private const val HEALTH_INDICATOR_NAME = "config"
    }
}
