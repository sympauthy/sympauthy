package com.sympauthy.business.manager

import com.sympauthy.config.model.*
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull


/**
 * Manager in charge of verifying if the configuration has completed and has no error.
 */
@Singleton
class ConfigReadinessManager(
    @Inject private val advancedConfig: AdvancedConfig,
    @Inject private val authConfig: AuthConfig,
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val claimsConfig: ClaimsConfig,
    @Inject private val clientsConfig: Flow<ClientsConfig>,
    @Inject private val featuresConfig: FeaturesConfig,
    @Inject private val mfaConfig: MfaConfig,
    @Inject private val rulesConfig: Flow<ScopeGrantingRulesConfig>,
    @Inject private val scopesConfig: ScopesConfig,
    @Inject private val uiConfig: UIConfig,
    @Inject private val urlsConfig: UrlsConfig
) {
    /**
     * List of synchronous configuration objects.
     */
    private val configs = listOf(
        advancedConfig,
        authConfig,
        authorizationFlowsConfig,
        claimsConfig,
        featuresConfig,
        mfaConfig,
        scopesConfig,
        uiConfig,
        urlsConfig
    )

    /**
     * List of asynchronous configuration objects.
     */
    private val flowConfigs = listOf(
        clientsConfig,
        rulesConfig
    )

    /**
     * Retrieves all configuration errors from the configurations.
     */
    suspend fun getConfigurationErrors(): List<Exception> {
        val asyncConfigs = flowConfigs.mapNotNull {
            try {
                it.firstOrNull()
            } catch (_: Throwable) {
                null
            }
        }
        return (asyncConfigs + configs).flatMap { it.configurationErrors ?: emptyList() }
    }
}
