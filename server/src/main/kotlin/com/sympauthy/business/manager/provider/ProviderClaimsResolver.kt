package com.sympauthy.business.manager.provider

import com.jayway.jsonpath.JsonPath
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.provider.openidconnect.ProviderIdTokenClaimsExtractor
import com.sympauthy.business.manager.provider.openidconnect.ProviderIdTokenManager
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2Tokens
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.client.UserInfoEndpointClient
import jakarta.inject.Inject
import jakarta.inject.Singleton

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
) {

    /**
     * Resolve [RawProviderClaims] from the [tokens] obtained from the [provider].
     *
     * If the token response contains an ID token, claims are extracted from it and the userinfo endpoint
     * is not called. Otherwise, the userinfo endpoint is used as a fallback.
     *
     * @param expectedNonce the nonce to validate against the ID token's nonce claim, if applicable.
     */
    suspend fun resolveClaims(
        provider: EnabledProvider,
        tokens: ProviderOAuth2Tokens,
        expectedNonce: String? = null
    ): RawProviderClaims {
        return resolveIdTokenClaimsOrNull(provider, tokens, expectedNonce)
            ?: resolveUserInfoClaims(provider, tokens)
    }

    private fun resolveIdTokenClaimsOrNull(
        provider: EnabledProvider,
        tokens: ProviderOAuth2Tokens,
        expectedNonce: String?
    ): RawProviderClaims? {
        val idTokenRaw = tokens.idToken ?: return null
        val openIdConnectConfig = provider.auth as? ProviderOpenIdConnectConfig

        val idTokenClaims = if (openIdConnectConfig != null) {
            providerIdTokenManager.validateAndExtractClaims(
                openIdConnectConfig = openIdConnectConfig,
                idTokenRaw = idTokenRaw,
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

        val rawUserInfoMap = userInfoEndpointClient.fetchUserInfo(
            userInfoConfig = userInfo,
            credentials = tokens
        )

        val document = JsonPath.parse(rawUserInfoMap)
        return userInfo.paths.entries
            .fold(RawProviderClaimsBuilder()) { builder, (pathKey, path) ->
                builder.withUserInfo(document, pathKey, path)
            }
            .build(provider)
    }
}