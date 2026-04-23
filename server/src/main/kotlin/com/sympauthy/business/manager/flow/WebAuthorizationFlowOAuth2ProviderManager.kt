package com.sympauthy.business.manager.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_CALLBACK_ENDPOINT
import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderClaimsResolver
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.CreateOrAssociateResult
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.Provider
import com.sympauthy.business.model.provider.config.ProviderAuthConfig
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2TokenRequest
import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2Tokens
import com.sympauthy.business.model.redirect.ProviderOAuth2AuthorizationRedirect
import com.sympauthy.business.model.redirect.ProviderOpenIdConnectAuthorizationRedirect
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.User
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.client.oauth2.TokenEndpointClient
import com.sympauthy.config.model.*
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.*

/**
 * Manager in charge of the authentication and registration of an end-user going through a web authorization flow
 * using an OAuth 2 or OIDC provider.
 */
@Singleton
open class WebAuthorizationFlowOAuth2ProviderManager(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val providerConfigManager: ProviderManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val providerClaimsResolver: ProviderClaimsResolver,
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val tokenEndpointClient: TokenEndpointClient,
    @Inject private val userManager: UserManager,
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    fun getOAuth2(provider: EnabledProvider): ProviderOAuth2Config {
        if (provider.auth !is ProviderOAuth2Config) {
            throw businessExceptionOf("provider.oauth2.unsupported")
        }
        return provider.auth
    }

    /**
     * Return the redirect uri that must be called by the third-party OAuth 2 provider after the user
     * has authenticated in an authorization code grant flow.
     */
    fun getRedirectUri(provider: Provider): URI {
        return uncheckedUrlsConfig.orThrow().getUri(
            FLOW_PROVIDER_ENDPOINTS + FLOW_PROVIDER_CALLBACK_ENDPOINT,
            "providerId" to provider.id
        )
    }

    /**
     * Return the URL to redirect the end-user to start the authorization process with the [Provider] identified by
     * [providerId].
     *
     * If the provider is not found or is disabled, an unrecoverable business exception is thrown.
     */
    suspend fun authorizeWithProvider(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        providerId: String
    ): URI {
        val provider = providerConfigManager.findByIdAndCheckEnabled(providerId)
        val state = authorizeAttemptManager.encodeState(authorizeAttempt)
        return when (val auth = provider.auth) {
            is ProviderOpenIdConnectConfig -> {
                val nonce = authorizeAttemptManager.setProvider(authorizeAttempt, providerId, generateNonce = true)!!
                ProviderOpenIdConnectAuthorizationRedirect(
                    openIdConnect = auth,
                    responseType = "code",
                    redirectUri = getRedirectUri(provider),
                    state = state,
                    nonce = nonce
                ).build()
            }

            is ProviderOAuth2Config -> {
                authorizeAttemptManager.setProvider(authorizeAttempt, providerId)
                ProviderOAuth2AuthorizationRedirect(
                    oauth2 = auth,
                    responseType = "code",
                    redirectUri = getRedirectUri(provider),
                    state = state
                ).build()
            }
        }
    }

    /**
     * Finalize the sign-in or sign-up of the end-user that authenticated through the [Provider] identified by [providerId].
     *
     * This method will perform the following actions:
     * - retrieve the access token from the [Provider] to check if the user is authenticated.
     * - retrieve end-user claims from the [Provider] and store them in this authorization server database.
     * - sign in the user if it already exists, according to the user-merging strategy.
     * - otherwise, sign up the user with the claims retrieved from the [Provider].
     */
    suspend fun signInOrSignUpUsingProvider(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        providerId: String?,
        authorizeCode: String?,
        providerError: String? = null,
        providerErrorDescription: String? = null
    ): Pair<AuthorizeAttempt, WebAuthorizationFlowStatus> {
        // Check if the provider returned an error instead of a code.
        if (!providerError.isNullOrBlank()) {
            throw businessExceptionOf(
                "flow.web_oauth2_provider.provider_error",
                "error" to providerError,
                "errorDescription" to (providerErrorDescription ?: "")
            )
        }

        // Those errors are marked unrecoverable because a proper provider should never end up in this case.
        // Therefore, the user retrying the request should not change the result.
        // We redirect the user to the error page so it can continue back to the application to retry.
        if (authorizeCode.isNullOrBlank()) {
            throw businessExceptionOf("flow.web_oauth2_provider.missing_code")
        }

        // Verify the provider ID in the callback matches the one stored during the authorization redirect.
        if (authorizeAttempt.providerId != null && authorizeAttempt.providerId != providerId) {
            throw businessExceptionOf(
                "flow.web_oauth2_provider.provider_mismatch",
                "expectedProviderId" to authorizeAttempt.providerId!!,
                "actualProviderId" to (providerId ?: "")
            )
        }

        val provider = providerConfigManager.findByIdAndCheckEnabled(providerId)

        val tokens = fetchTokens(provider, provider.auth, authorizeCode)
        val expectedNonce = authorizeAttemptManager.buildProviderNonceOrNull(authorizeAttempt)
        val rawUserInfo = providerClaimsResolver.resolveClaims(provider, tokens, expectedNonce)

        val existingUserInfo = providerClaimsManager.findByProviderAndSubject(
            provider = provider,
            subject = rawUserInfo.subject
        )

        val userId = if (existingUserInfo == null) {
            createOrAssociateUserWithProviderUserInfo(provider, rawUserInfo).user.id
        } else {
            providerClaimsManager.refreshUserInfo(existingUserInfo, rawUserInfo)
            existingUserInfo.userId
        }
        val updatedAuthorizeAttempt = authorizeAttemptManager.setAuthenticatedUserId(authorizeAttempt, userId)

        return webAuthorizationFlowManager.getStatusAndCompleteIfNecessary(
            authorizeAttempt = updatedAuthorizeAttempt
        )
    }

    /**
     * Create a new [com.sympauthy.business.model.user.User] or associate to an existing [com.sympauthy.business.model.user.User].
     * Then update the provider user info with the newly collected [providerUserInfo].
     *
     * Depending on ```auth.user-merging-enabled```, we may instead associate the [providerUserInfo] to
     * an existing user based on the configured identifier claims.
     */
    @Transactional
    open suspend fun createOrAssociateUserWithProviderUserInfo(
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val authConfig = uncheckedAuthConfig.orThrow()
        val identifierClaims = resolveIdentifierClaims(authConfig, provider, providerUserInfo)
        return if (authConfig.userMergingEnabled) {
            createOrAssociateUserByIdentifierClaimsWithProviderUserInfo(identifierClaims, provider, providerUserInfo)
        } else {
            createUserWithProviderUserInfo(identifierClaims, provider, providerUserInfo)
        }
    }

    /**
     * Create a new [com.sympauthy.business.model.user.User] or associate it to an existing user
     * that has matching values for all configured identifier claims.
     *
     * The identifier claim values are collected and copied as first-party data. We want this information
     * to be stable and not be affected by changes from the third party in the future.
     * Otherwise, an update from a provider may break our uniqueness and cause uncontrolled side effects.
     */
    @Transactional
    internal open suspend fun createOrAssociateUserByIdentifierClaimsWithProviderUserInfo(
        identifierClaims: Map<String, Pair<Claim, String>>,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val identifierMap = identifierClaims.map { (claimId, pair) -> claimId to pair.second }.toMap()
        val existingUser = userManager.findByIdentifierClaims(identifierMap)

        val user = existingUser ?: userManager.createUser().also { newUser ->
            saveIdentifierClaims(newUser, identifierClaims)
        }

        providerClaimsManager.saveUserInfo(
            provider = provider,
            userId = user.id,
            rawProviderClaims = providerUserInfo
        )
        return CreateOrAssociateResult(
            created = existingUser == null,
            user = user
        )
    }

    /**
     * Create a new [com.sympauthy.business.model.user.User] with the provider user info.
     * Without user merging, if a user already exists with the same identifier claims, throw an error
     * as the user must sign in with their existing account.
     */
    @Transactional
    internal open suspend fun createUserWithProviderUserInfo(
        identifierClaims: Map<String, Pair<Claim, String>>,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val identifierMap = identifierClaims.map { (claimId, pair) -> claimId to pair.second }.toMap()
        val existingUser = userManager.findByIdentifierClaims(identifierMap)
        if (existingUser != null) {
            throw businessExceptionOf("user.create_with_provider.existing_user")
        }

        val user = userManager.createUser()
        saveIdentifierClaims(user, identifierClaims)
        providerClaimsManager.saveUserInfo(
            provider = provider,
            userId = user.id,
            rawProviderClaims = providerUserInfo
        )
        return CreateOrAssociateResult(
            created = true,
            user = user
        )
    }

    /**
     * Extract identifier claim values from [providerUserInfo] and resolve the corresponding
     * [Claim] business objects.
     */
    private suspend fun resolveIdentifierClaims(
        authConfig: EnabledAuthConfig,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): Map<String, Pair<Claim, String>> {
        return authConfig.identifierClaims.associateWith { claimId ->
            val value = providerUserInfo.getClaimValueOrNull(claimId)
                ?: throw businessExceptionOf(
                    "user.create_with_provider.missing_identifier_claim",
                    "providerId" to provider.id,
                    "claim" to claimId
                )
            val claim = claimManager.findByIdOrNull(claimId)
                ?: throw businessExceptionOf(
                    "user.create_with_provider.missing_identifier_claim_config",
                    "claim" to claimId
                )
            claim to value
        }
    }

    private suspend fun saveIdentifierClaims(
        user: User,
        identifierClaims: Map<String, Pair<Claim, String>>
    ) {
        collectedClaimManager.update(
            user = user,
            updates = identifierClaims.map { (_, claimAndValue) ->
                CollectedClaimUpdate(
                    claim = claimAndValue.first,
                    value = Optional.of(claimAndValue.second)
                )
            }
        )
    }

    suspend fun fetchTokens(
        provider: Provider,
        auth: ProviderAuthConfig,
        authorizeCode: String
    ): ProviderOAuth2Tokens {
        val oauth2Config = when (auth) {
            is ProviderOAuth2Config -> auth
            is ProviderOpenIdConnectConfig -> ProviderOAuth2Config(
                clientId = auth.clientId,
                clientSecret = auth.clientSecret,
                scopes = auth.scopes,
                authorizationUri = auth.authorizationUri,
                tokenUri = auth.tokenUri
            )
        }
        val request = ProviderOAuth2TokenRequest(
            oauth2 = oauth2Config,
            authorizeCode = authorizeCode,
            redirectUri = getRedirectUri(provider)
        )
        val response = tokenEndpointClient.fetchTokens(request)

        // Verify token type is Bearer (RFC 6749 §7.1)
        if (!response.tokenType.equals("Bearer", ignoreCase = true)) {
            throw businessExceptionOf(
                "flow.web_oauth2_provider.unsupported_token_type",
                "tokenType" to response.tokenType,
                "providerId" to provider.id
            )
        }

        return ProviderOAuth2Tokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            idToken = response.idToken
        )
    }
}
