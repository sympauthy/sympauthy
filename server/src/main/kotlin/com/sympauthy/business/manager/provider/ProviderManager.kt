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
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceReadyEvent
import io.micronaut.scheduling.annotation.Async
import io.reactivex.rxjava3.core.Single
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx3.await
import java.net.URI
import java.util.*

/**
 * Manager in charge of resolving providers from the validated configuration into fully usable [Provider] instances.
 *
 * For OAuth2 providers, this is a direct mapping from [ProviderOAuth2InputConfig] to [ProviderOAuth2Config].
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
) : ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()

    private val configuredProviders = Single
        .create {
            it.onSuccess(resolveProviders())
        }
        .cache()

    @Async
    override fun onApplicationEvent(event: ServiceReadyEvent) {
        configuredProviders.subscribe()
    }

    suspend fun listProviders(): List<Provider> {
        return configuredProviders.await()
    }

    suspend fun listEnabledProviders(): List<EnabledProvider> {
        return listProviders()
            .filterIsInstance<EnabledProvider>()
    }

    /**
     * Return the [EnabledProvider] identified by [id]. Otherwise, throw an internal business exception if the provider
     * does not exist or is disabled by configuration.
     */
    suspend fun findByIdAndCheckEnabled(id: String?): EnabledProvider {
        val provider = listProviders().firstOrNull { it.id == id }
            ?: throw businessExceptionOf(
                detailsId = "provider.missing",
                "providerId" to (id ?: "")
            )
        return (provider as? EnabledProvider)
            ?: throw businessExceptionOf(
                detailsId = "provider.disabled",
                "providerId" to (id ?: "")
            )
    }

    private fun resolveProviders(): List<Provider> {
        val enabledConfig = providersConfig as? EnabledProvidersConfig ?: return emptyList()
        return enabledConfig.providers.map { config ->
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

    internal fun resolveProvider(config: ProviderConfig): EnabledProvider {
        val auth = when (val input = config.auth) {
            is ProviderOAuth2InputConfig -> ProviderOAuth2Config(
                clientId = input.clientId,
                clientSecret = input.clientSecret,
                scopes = input.scopes,
                authorizationUri = input.authorizationUri,
                tokenUri = input.tokenUri
            )
            is ProviderOpenIdConnectInputConfig -> {
                val discovery = runBlocking { openIdConnectDiscoveryClient.fetchDiscovery(input.issuer) }
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
