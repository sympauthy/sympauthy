package com.sympauthy.business.manager.provider

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.provider.DisabledProvider
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.Provider
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import com.sympauthy.client.openidconnect.OpenIdConnectDiscoveryClient
import com.sympauthy.config.model.*
import com.sympauthy.exception.LocalizedException
import com.sympauthy.monitoring.Condition
import com.sympauthy.monitoring.Conditions
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.*
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager in charge of resolving providers from the validated configuration into fully usable [Provider] instances.
 *
 * Each provider is resolved concurrently at startup. For OAuth2 providers, this is a direct mapping.
 * For OpenID Connect providers, this involves fetching the discovery document to resolve endpoints.
 *
 * A provider whose discovery cannot be fetched starts disabled and its condition degraded, and its
 * resolution is retried until it answers.
 *
 * Configuration validation (parsing YAML, checking required fields) is handled by
 * [com.sympauthy.config.factory.ProvidersConfigFactory].
 */
@Singleton
open class ProviderManager(
    @Inject private val providersConfig: ProvidersConfig,
    @Inject private val openIdConnectDiscoveryClient: OpenIdConnectDiscoveryClient,
    @Inject private val conditions: Conditions
) {

    /**
     * The scope a provider is resolved on, and retried on while its discovery cannot be fetched.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val providers = ConcurrentHashMap<String, Deferred<Provider>>()

    @PostConstruct
    fun init() {
        val enabledConfig = providersConfig as? EnabledProvidersConfig ?: return
        for (config in enabledConfig.providers) {
            providers[config.id] = scope.async {
                try {
                    resolveProvider(config).also { conditions.reportSuccess(Condition.Provider(config.id)) }
                } catch (e: Exception) {
                    conditions.reportFailure(Condition.Provider(config.id), e)
                    retryResolution(config)
                    DisabledProvider(config.id, e.toLocalizedException(config))
                }
            }
        }
    }

    /**
     * Resolve the provider [config] declares again every [RESOLUTION_RETRY_INTERVAL] until it resolves, and
     * from then on serve it like a provider that resolved at startup.
     *
     * A provider whose discovery cannot be fetched at startup is otherwise disabled until the server is
     * restarted, and its condition reported degraded with nothing that could ever see it recover. Every
     * failed attempt is reported to its condition, which counts it.
     */
    private fun retryResolution(config: ProviderConfig) = scope.launch {
        while (true) {
            delay(RESOLUTION_RETRY_INTERVAL.toMillis())
            try {
                providers[config.id] = CompletableDeferred(resolveProvider(config))
                conditions.reportSuccess(Condition.Provider(config.id))
                return@launch
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                conditions.reportFailure(Condition.Provider(config.id), e)
            }
        }
    }

    private fun Exception.toLocalizedException(config: ProviderConfig) = this as? LocalizedException
        ?: LocalizedException(
            detailsId = "provider.openid_connect.discovery_failed",
            values = mapOf("issuer" to config.id),
            throwable = this
        )

    suspend fun listProviders(): List<Provider> {
        return providers.values.awaitAll()
    }

    suspend fun listEnabledProviders(): List<EnabledProvider> {
        return listProviders()
            .filterIsInstance<EnabledProvider>()
    }

    /**
     * Return the [EnabledProvider] identified by [id].
     * Suspends until the provider has finished resolving.
     * Throws an internal business exception if the provider does not exist or is disabled.
     */
    suspend fun findByIdAndCheckEnabled(id: String?): EnabledProvider {
        val deferred = providers[id]
            ?: throw businessExceptionOf(
                detailsId = "provider.missing",
                "providerId" to (id ?: "")
            )
        val provider = deferred.await()
        return (provider as? EnabledProvider)
            ?: throw businessExceptionOf(
                detailsId = "provider.disabled",
                "providerId" to (id ?: "")
            )
    }

    internal suspend fun resolveProvider(config: ProviderConfig): EnabledProvider {
        val auth = when (val input = config.auth) {
            is ProviderOAuth2InputConfig -> ProviderOAuth2Config(
                clientId = input.clientId,
                clientSecret = input.clientSecret,
                scopes = input.scopes,
                authorizationUri = input.authorizationUri,
                tokenUri = input.tokenUri
            )

            is ProviderOpenIdConnectInputConfig -> {
                val discovery = openIdConnectDiscoveryClient.fetchDiscovery(input.issuer)
                ProviderOpenIdConnectConfig(
                    clientId = input.clientId,
                    clientSecret = input.clientSecret,
                    scopes = input.scopes,
                    issuer = input.issuer,
                    authorizationUri = URI.create(discovery.authorizationEndpoint),
                    tokenUri = URI.create(discovery.tokenEndpoint),
                    jwksUri = URI.create(discovery.jwksUri)
                )
            }
        }
        return EnabledProvider(
            id = config.id,
            name = config.name,
            userInfo = config.userInfo,
            auth = auth
        )
    }

    companion object {
        /**
         * How long a provider that could not be resolved waits before it is tried again. It reuses the
         * conditions' quiet period rather than adding a number of its own to the ones the design fixes.
         */
        private val RESOLUTION_RETRY_INTERVAL: Duration = Conditions.QUIET_PERIOD
    }
}
