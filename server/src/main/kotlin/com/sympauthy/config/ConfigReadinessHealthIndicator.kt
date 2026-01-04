package com.sympauthy.config

import com.sympauthy.business.manager.ConfigReadinessManager
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

@Singleton
@Readiness
open class ConfigReadinessHealthIndicator(
    @Inject private val configReadinessManager: ConfigReadinessManager,
    @Inject @param:ErrorMessages private val messageSource: MessageSource,
) : HealthIndicator {

    override fun getResult(): Publisher<HealthResult> {
        return flow {
            val configurationErrors = configReadinessManager.getConfigurationErrors()
            val builder = HealthResult.builder(HEALTH_INDICATOR_NAME)
            if (configurationErrors.isEmpty()) {
                builder.status(UP)
            } else {
                builder.status(DOWN)
                builder.details(configurationErrors.associate { it.getKeyAndLocalizedMessage(messageSource) })
            }
            emit(builder.build())
        }.asPublisher()
    }

    companion object {
        private const val HEALTH_INDICATOR_NAME = "config"
    }
}
