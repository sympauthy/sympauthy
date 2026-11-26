package com.sympauthy.business.provider.manager

import com.jayway.jsonpath.JsonPath
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.provider.config.model.ProviderOpenIdConnectConfig
import com.sympauthy.business.provider.model.EnabledProvider
import com.sympauthy.business.provider.model.RawProviderClaims
import com.sympauthy.business.provider.oauth2.model.ProviderOAuth2Tokens
import com.sympauthy.business.provider.openidconnect.manager.ProviderIdTokenClaimsExtractor
import com.sympauthy.business.provider.openidconnect.manager.ProviderIdTokenManager
import com.sympauthy.client.UserInfoEndpointClient
import com.sympauthy.client.remoteErrorType
import com.sympauthy.client.remoteOutcome
import com.sympauthy.client.remoteResponseOrNull
import com.sympauthy.platform.monitoring.manager.RemoteCallMonitor
import com.sympauthy.platform.monitoring.model.Condition
import com.sympauthy.platform.monitoring.model.Outcome
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Resolves [RawProviderClaims] from a provider's token response.
 *
 * Claims are extracted from the first available source:
 * 1. The ID token, if present in the token response.
 * 2. The userinfo endpoint, if configured for the provider.
 */
@Singleton
class ProviderClaimsResolver(
    @Inject private val providerIdTokenManager: ProviderIdTokenManager,
    @Inject private val providerIdTokenClaimsExtractor: ProviderIdTokenClaimsExtractor,
    @Inject private val userInfoEndpointClient: UserInfoEndpointClient,
    @Inject private val remoteCallMonitor: RemoteCallMonitor,
) {

    /**
     * Resolve [RawProviderClaims] from the [tokens] the [provider] has just issued.
     *
     * If the token response contains an ID token, claims are extracted from it and the userinfo endpoint
     * is not called. Otherwise, the userinfo endpoint is used as a fallback.
     *
     * [expectedNonce] is matched against the ID token's `nonce` claim, so it is read on the first of those two
     * paths only — a provider that answered without an ID token has nothing to match it against.
     *
     * Throws `provider.user_info.failed` where the userinfo endpoint cannot be reached, does not answer in time
     * or answers with an error status or with something that is not a JSON object.
     */
    suspend fun resolveClaims(
        provider: EnabledProvider,
        tokens: ProviderOAuth2Tokens,
        expectedNonce: String? = null
    ): RawProviderClaims {
        return resolveIdTokenClaimsOrNull(provider, tokens, expectedNonce)
            ?: resolveUserInfoClaims(provider, tokens)
    }

    private suspend fun resolveIdTokenClaimsOrNull(
        provider: EnabledProvider,
        tokens: ProviderOAuth2Tokens,
        expectedNonce: String?
    ): RawProviderClaims? {
        val idTokenRaw = tokens.idToken ?: return null
        val openIdConnectConfig = provider.auth as? ProviderOpenIdConnectConfig

        val idTokenClaims = if (openIdConnectConfig != null) {
            providerIdTokenManager.validateAndExtractClaims(
                providerId = provider.id,
                openIdConnectConfig = openIdConnectConfig,
                idTokenRaw = idTokenRaw,
                accessToken = tokens.accessToken,
                expectedNonce = expectedNonce
            )
        } else {
            // For OAuth2 providers that unexpectedly return an ID token,
            // we still extract claims but skip signature validation.
            providerIdTokenClaimsExtractor.decodeClaimsWithoutValidation(idTokenRaw)
        }

        return providerIdTokenClaimsExtractor.extractClaims(provider.id, idTokenClaims)
    }

    private suspend fun resolveUserInfoClaims(
        provider: EnabledProvider,
        tokens: ProviderOAuth2Tokens
    ): RawProviderClaims {
        val userInfo = provider.userInfo
            ?: throw businessExceptionOf("provider.user_info.not_configured", "providerId" to provider.id)

        val rawUserInfoMap = try {
            remoteCallMonitor.observe(
                Condition.Provider(provider.id), "user_info", Throwable::userInfoOutcome
            ) {
                userInfoEndpointClient.fetchUserInfo(
                    userInfoConfig = userInfo,
                    credentials = tokens
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            throw internalBusinessExceptionOf("provider.user_info.failed", e, "providerId" to provider.id)
        }

        val document = JsonPath.parse(rawUserInfoMap)
        return userInfo.paths.entries
            .fold(RawProviderClaimsBuilder()) { builder, (pathKey, path) ->
                builder.withUserInfo(document, pathKey, path)
            }
            .build(provider)
    }
}

/**
 * How a call to the userinfo endpoint that ended in this failure is recorded. Unlike [remoteOutcome], it holds a
 * `4xx` as an outage: the access token the provider has just issued is all the call sends, so a refusal is the
 * provider failing as much as an outage is.
 */
private fun Throwable.userInfoOutcome(): Outcome {
    return when (remoteResponseOrNull()?.code()) {
        in 400..499 -> Outcome.Error(remoteErrorType())
        else -> remoteOutcome()
    }
}
