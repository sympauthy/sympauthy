package com.sympauthy.config

import com.sympauthy.config.exception.ConfigurationException
import com.sympauthy.config.model.*
import com.sympauthy.exception.LocalizedException
import com.sympauthy.server.ErrorMessages
import com.sympauthy.util.DEFAULT_ENVIRONMENT
import com.sympauthy.util.isDefaultActive
import com.sympauthy.util.loggerForClass
import io.micronaut.context.MessageSource
import io.micronaut.context.env.Environment
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceReadyEvent
import io.micronaut.health.HealthStatus.DOWN
import io.micronaut.health.HealthStatus.UP
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import io.micronaut.management.health.indicator.annotation.Readiness
import io.micronaut.scheduling.annotation.Async
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.runBlocking
import org.reactivestreams.Publisher
import java.util.*

@Singleton
@Readiness
open class ConfigReadinessIndicator(
    @Inject private val environment: Environment,
    @ErrorMessages @Inject private val messageSource: MessageSource,
    @Inject private val advancedConfig: AdvancedConfig,
    @Inject private val authConfig: AuthConfig,
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val claimsConfig: ClaimsConfig,
    @Inject private val clientsConfig: Flow<ClientsConfig>,
    @Inject private val featuresConfig: FeaturesConfig,
    @Inject private val passwordAuthConfig: PasswordAuthConfig,
    @Inject private val rulesConfig: Flow<ScopeGrantingRulesConfig>,
    @Inject private val scopesConfig: ScopesConfig,
    @Inject private val uiConfig: UIConfig,
    @Inject private val urlsConfig: UrlsConfig
) : HealthIndicator, ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()

    private val configs = listOf(
        advancedConfig,
        authConfig,
        authorizationFlowsConfig,
        claimsConfig,
        featuresConfig,
        passwordAuthConfig,
        scopesConfig,
        uiConfig,
        urlsConfig
    )

    private val flowConfigs = listOf(
        clientsConfig,
        rulesConfig
    )

    private suspend fun getConfigurationErrors(): List<Exception> {
        val asyncConfigs = flowConfigs.mapNotNull {
            try {
                it.firstOrNull()
            } catch (e: Throwable) {
                null
            }
        }
        return (asyncConfigs + configs).flatMap { it.configurationErrors ?: emptyList() }
    }

    override fun getResult(): Publisher<HealthResult> {
        return flow {
            val configurationErrors = getConfigurationErrors()
            val builder = HealthResult.builder(HEALTH_INDICATOR_NAME)
            if (configurationErrors.isEmpty()) {
                builder.status(UP)
            } else {
                builder.status(DOWN)
                builder.details(configurationErrors.associate(::getKeyAndLocalizedMessage))
            }
            emit(builder.build())
        }.asPublisher()
    }

    @Async
    override fun onApplicationEvent(event: ServiceReadyEvent) = runBlocking {
        val configurationErrors = getConfigurationErrors()
        if (configurationErrors.isEmpty()) {
            logger.info("No error detected in the configuration.")
        } else {
            logger.error("One or more errors detected in the configuration. This application will NOT OPERATE PROPERLY.")
            configurationErrors
                .map(::getKeyAndLocalizedMessage)
                .forEach { (key, localizedErrorMessage) ->
                    logger.error("- $key: $localizedErrorMessage")
                }

            if (!environment.isDefaultActive) {
                logger.info("The '$DEFAULT_ENVIRONMENT' environment is not enabled meaning you are missing default configuration of SympAuthy. If it is not intentional, you can enable it by adding '$DEFAULT_ENVIRONMENT' to micronaut environments. Either by param '--micronaut-environments=$DEFAULT_ENVIRONMENT' or by environment variable 'MICRONAUT_ENVIRONMENTS=$DEFAULT_ENVIRONMENT'.")
            }
        }
    }

    private fun getKeyAndLocalizedMessage(error: Exception): Pair<String, String?> {
        return when (error) {
            is LocalizedException -> {
                val key = error.values["key"]?.toString() ?: ""
                val localizedErrorMessage = messageSource.getMessage(error.detailsId, Locale.US, error.values)
                    .orElse(error.detailsId)
                key to localizedErrorMessage
            }

            is ConfigurationException -> {
                val localizedErrorMessage = messageSource.getMessage(error.messageId, Locale.US, error.values)
                    .orElse(error.messageId)
                error.key to localizedErrorMessage
            }

            else -> error.javaClass.name to error.message
        }
    }

    companion object {
        private const val HEALTH_INDICATOR_NAME = "config"
    }
}
