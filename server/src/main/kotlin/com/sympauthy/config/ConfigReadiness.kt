package com.sympauthy.config

import com.sympauthy.config.model.*
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

/**
 * The configuration answering, across every domain it is split into, whether it is usable.
 *
 * This is a departure from the artifacts a configuration domain is otherwise written as: everything
 * else here is per-domain, and this holds every domain's model at once. It has to, because an
 * operator is owed one verdict on the file they wrote rather than one per section of it, and no
 * single domain can give that.
 *
 * Every configuration bean therefore belongs in this constructor. One left out is a section whose
 * errors never reach readiness, which looks from the outside exactly like a section with no errors.
 */
@Singleton
class ConfigReadiness(
    @Inject private val actAsRulesConfig: Flow<ActAsRulesConfig>,
    @Inject private val adminConfig: AdminConfig,
    @Inject private val advancedConfig: AdvancedConfig,
    @Inject private val audiencesConfig: AudiencesConfig,
    @Inject private val authConfig: AuthConfig,
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
    @Inject private val claimTemplatesConfig: ClaimTemplatesConfig,
    @Inject private val claimsConfig: ClaimsConfig,
    @Inject private val clientTemplatesConfig: Flow<ClientTemplatesConfig>,
    @Inject private val clientsConfig: Flow<ClientsConfig>,
    @Inject private val corsConfig: CorsConfig,
    @Inject private val featuresConfig: FeaturesConfig,
    @Inject private val mfaConfig: MfaConfig,
    @Inject private val rulesConfig: Flow<ScopeGrantingRulesConfig>,
    @Inject private val scopeTemplatesConfig: ScopeTemplatesConfig,
    @Inject private val scopesConfig: ScopesConfig,
    @Inject private val uiConfig: UIConfig,
    @Inject private val urlsConfig: UrlsConfig,
    @Inject private val providersConfig: ProvidersConfig,
    @Inject private val bootstrapInvitationsConfig: BootstrapInvitationsConfig
) {
    /**
     * List of synchronous configuration objects.
     */
    private val configs = listOf(
        adminConfig,
        advancedConfig,
        audiencesConfig,
        authConfig,
        authorizationFlowsConfig,
        bootstrapInvitationsConfig,
        claimTemplatesConfig,
        claimsConfig,
        corsConfig,
        featuresConfig,
        mfaConfig,
        scopeTemplatesConfig,
        scopesConfig,
        uiConfig,
        urlsConfig,
        providersConfig
    )

    /**
     * List of asynchronous configuration objects.
     */
    private val flowConfigs = listOf(
        actAsRulesConfig,
        clientTemplatesConfig,
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
