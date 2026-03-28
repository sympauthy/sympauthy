package com.sympauthy.business.manager.provider

import com.jayway.jsonpath.JsonPath
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.provider.DisabledProvider
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.Provider
import com.sympauthy.business.model.provider.ProviderUserInfoPathKey
import com.sympauthy.business.model.provider.ProviderUserInfoPathKey.EMAIL
import com.sympauthy.business.model.provider.ProviderUserInfoPathKey.SUB
import com.sympauthy.business.model.provider.config.ProviderAuthConfig
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import com.sympauthy.business.model.provider.config.ProviderUserInfoConfig
import com.sympauthy.client.openidconnect.OpenIdConnectDiscoveryClient
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.config.properties.ProviderConfigurationProperties
import com.sympauthy.config.properties.ProviderConfigurationProperties.Companion.PROVIDERS_KEY
import com.sympauthy.config.util.convertToEnum
import com.sympauthy.config.util.getStringOrThrow
import com.sympauthy.config.util.getUriOrThrow
import com.sympauthy.exception.LocalizedException
import com.sympauthy.exception.localizedExceptionOf
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
 * Manager in charge of verifying the providers available in the configuration and transforming them
 * in a more usable form for the rest of the business logic.
 */
@Singleton
open class ProviderConfigManager(
    @Inject private val providers: List<ProviderConfigurationProperties>,
    @param:ErrorMessages @Inject private val messageSource: MessageSource,
    @Inject private val authConfig: AuthConfig,
    @Inject private val openIdConnectDiscoveryClient: OpenIdConnectDiscoveryClient
) : ApplicationEventListener<ServiceReadyEvent> {

    private val logger = loggerForClass()

    private val configuredProviders = Single
        .create {
            it.onSuccess(configureProviders())
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

    private fun configureProviders(): List<Provider> {
        return providers.map {
            try {
                configureProvider(it)
            } catch (e: LocalizedException) {
                val localizedErrorMessage = messageSource.getMessage(e.detailsId, Locale.US, e.values)
                    .orElse(e.detailsId)
                logger.error("Failed to configure ${it.id}: $localizedErrorMessage")
                DisabledProvider(it.id, e)
            }
        }
    }

    internal fun configureProvider(config: ProviderConfigurationProperties): EnabledProvider {
        val auth = configureProviderAuth(config)
        val userInfo = when (auth) {
            is ProviderOpenIdConnectConfig -> null
            else -> configureProviderUserInfo(config)
        }
        return EnabledProvider(
            id = config.id,
            name = getStringOrThrow(config, "$PROVIDERS_KEY.name", ProviderConfigurationProperties::name),
            userInfo = userInfo,
            auth = auth
        )
    }

    private fun configureProviderUserInfo(config: ProviderConfigurationProperties): ProviderUserInfoConfig {
        val userInfo = config.userInfo ?: throw localizedExceptionOf(
            "config.provider.user_info.missing"
        )

        return ProviderUserInfoConfig(
            uri = getUriOrThrow(
                userInfo,
                "${PROVIDERS_KEY}.user-info.url",
                ProviderConfigurationProperties.UserInfoConfig::url
            ),
            paths = configureProviderUserInfoPaths(userInfo)
        )
    }

    private fun configureProviderUserInfoPaths(
        userInfo: ProviderConfigurationProperties.UserInfoConfig
    ): Map<ProviderUserInfoPathKey, JsonPath> {
        val userInfoPathsKey = "$PROVIDERS_KEY.user-info.paths"
        val userInfoPaths = userInfo.paths ?: throw localizedExceptionOf(
            "config.missing",
            "key" to userInfoPathsKey
        )
        val paths = userInfoPaths
            .map { (key, value) ->
                val pathKey = convertToEnum<ProviderUserInfoPathKey>(
                    "$userInfoPathsKey.$key", key
                )
                val rawPath = value ?: throw localizedExceptionOf(
                    "config.provider.user_info.invalid_value",
                    "key" to "$userInfoPathsKey.$key"
                )
                val path = try {
                    JsonPath.compile(rawPath)
                } catch (e: Throwable) {
                    throw LocalizedException(
                        detailsId = "config.provider.user_info.invalid_value",
                        values = mapOf(
                            "key" to "$userInfoPathsKey.$key"
                        ),
                        throwable = e
                    )
                }
                pathKey to path
            }
            .toMap()
        if (paths[SUB] == null) {
            throw localizedExceptionOf(
                "config.provider.user_info.missing_subject_key",
                "key" to "${PROVIDERS_KEY}.user-info.paths"
            )
        }
        if (authConfig.orThrow().userMergingEnabled && paths[EMAIL] == null) {
            throw localizedExceptionOf(
                "config.provider.user_info.missing_email_key",
                "key" to "${PROVIDERS_KEY}.user-info.paths"
            )
        }
        return paths
    }

    private fun configureProviderAuth(config: ProviderConfigurationProperties): ProviderAuthConfig {
        return when {
            config.oidc != null -> configureProviderOpenIdConnect(config, config.oidc!!)
            config.oauth2 != null -> configureProviderOAuth2(config, config.oauth2!!)
            else -> throw localizedExceptionOf(
                "config.auth.missing"
            )
        }
    }

    private fun configureProviderOAuth2(
        config: ProviderConfigurationProperties,
        oauth2: ProviderConfigurationProperties.OAuth2Config
    ): ProviderOAuth2Config {
        return ProviderOAuth2Config(
            clientId = getStringOrThrow(
                oauth2,
                "${PROVIDERS_KEY}.${config.id}.client-id",
                ProviderConfigurationProperties.OAuth2Config::clientId
            ),
            clientSecret = getStringOrThrow(
                oauth2,
                "${PROVIDERS_KEY}.${config.id}.client-secret",
                ProviderConfigurationProperties.OAuth2Config::clientSecret
            ),
            scopes = oauth2.scopes,
            authorizationUri = getUriOrThrow(
                oauth2,
                "${PROVIDERS_KEY}.${config.id}.authorization-url",
                ProviderConfigurationProperties.OAuth2Config::authorizationUrl
            ),
            tokenUri = getUriOrThrow(
                oauth2,
                "${PROVIDERS_KEY}.${config.id}.token-url",
                ProviderConfigurationProperties.OAuth2Config::tokenUrl
            )
        )
    }

    private fun configureProviderOpenIdConnect(
        config: ProviderConfigurationProperties,
        oidc: ProviderConfigurationProperties.OpenIdConnectConfig
    ): ProviderOpenIdConnectConfig {
        val keyPrefix = "${PROVIDERS_KEY}.${config.id}.oidc"
        val issuer = getUriOrThrow(oidc, "$keyPrefix.issuer", ProviderConfigurationProperties.OpenIdConnectConfig::issuer)
        val clientId = getStringOrThrow(oidc, "$keyPrefix.client-id", ProviderConfigurationProperties.OpenIdConnectConfig::clientId)
        val clientSecret = getStringOrThrow(oidc, "$keyPrefix.client-secret", ProviderConfigurationProperties.OpenIdConnectConfig::clientSecret)

        val discovery = runBlocking { openIdConnectDiscoveryClient.fetchDiscovery(issuer) }

        val scopes = (oidc.scopes ?: listOf("openid")).let { scopes ->
            if ("openid" !in scopes) listOf("openid") + scopes else scopes
        }

        return ProviderOpenIdConnectConfig(
            clientId = clientId,
            clientSecret = clientSecret,
            scopes = scopes,
            issuer = issuer,
            authorizationUri = URI.create(discovery.authorizationEndpoint),
            tokenUri = URI.create(discovery.tokenEndpoint),
            jwksUri = URI.create(discovery.jwksUri)
        )
    }
}
