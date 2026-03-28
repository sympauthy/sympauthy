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
import com.sympauthy.server.ErrorMessages
import com.sympauthy.util.loggerForClass
import io.micronaut.context.MessageSource
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.*
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager in charge of resolving providers from the validated configuration into fully usable [Provider] instances.
 *
 * Each provider is resolved concurrently at startup. For OAuth2 providers, this is a direct mapping.
 * For OpenID Connect providers, this involves fetching the discovery document to resolve endpoints.
 *
 * Configuration validation (parsing YAML, checking required fields) is handled by
 * [com.sympauthy.config.factory.ProvidersConfigFactory].
 */
@Singleton
open class ProviderManager(
    @Inject private val providersConfig: ProvidersConfig,
    @param:ErrorMessages @Inject private val messageSource: MessageSource,
    @Inject private val openIdConnectDiscoveryClient: OpenIdConnectDiscoveryClient
) {

    private val logger = loggerForClass()

    private val providers = ConcurrentHashMap<String, Deferred<Provider>>()

    @PostConstruct
    fun init() {
        val enabledConfig = providersConfig as? EnabledProvidersConfig ?: return
        val scope = CoroutineScope(Dispatchers.IO)
        for (config in enabledConfig.providers) {
            providers[config.id] = scope.async {
                try {
                    resolveProvider(config)
                } catch (e: LocalizedException) {
                    val localizedErrorMessage = messageSource.getMessage(e.detailsId, Locale.US, e.values)
                        .orElse(e.detailsId)
                    logger.error("Failed to resolve provider ${config.id}: $localizedErrorMessage")
                    DisabledProvider(config.id, e)
                } catch (e: Exception) {
                    logger.error("Failed to resolve provider ${config.id}: ${e.message}")
                    DisabledProvider(config.id, LocalizedException(
                        detailsId = "config.provider.openid_connect.discovery_failed",
                        values = mapOf("issuer" to config.id),
                        throwable = e
                    ))
                }
            }
        }
    }

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
}
