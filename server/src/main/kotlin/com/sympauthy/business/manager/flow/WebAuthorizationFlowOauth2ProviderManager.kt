package com.sympauthy.business.manager.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_CALLBACK_ENDPOINT
import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderConfigManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.CreateOrAssociateResult
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.oauth2.OnGoingAuthorizeAttempt
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.Provider
import com.sympauthy.business.model.provider.config.ProviderOauth2Config
import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2TokenRequest
import com.sympauthy.business.model.provider.oauth2.ProviderOauth2Tokens
import com.sympauthy.business.model.redirect.ProviderOauth2AuthorizationRedirect
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.client.oauth2.TokenEndpointClient
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.EnabledAuthConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.*

/**
 * Manager in charge of the authentication and registration of an end-user going through a web authorization flow
 * using an OAuth 2 provider.
 */
@Singleton
open class WebAuthorizationFlowOauth2ProviderManager(
    @Inject private val authorizeAttemptManager: AuthorizeAttemptManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val providerConfigManager: ProviderConfigManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val tokenEndpointClient: TokenEndpointClient,
    @Inject private val userManager: UserManager,
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    fun getOauth2(provider: EnabledProvider): ProviderOauth2Config {
        if (provider.auth !is ProviderOauth2Config) {
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
        val oauth2 = getOauth2(provider)
        return ProviderOauth2AuthorizationRedirect(
            oauth2 = oauth2,
            responseType = "code",
            redirectUri = getRedirectUri(provider),
            state = authorizeAttemptManager.encodeState(authorizeAttempt)
        ).build()
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
        authorizeCode: String?
    ): Pair<AuthorizeAttempt, WebAuthorizationFlowStatus> {
        // Those errors are marked unrecoverable because a proper provider should never end up in this case.
        // Therefore, the user retrying the request should not change the result.
        // We redirect the user to the error page so it can continue back to the application to retry.
        if (authorizeCode.isNullOrBlank()) {
            throw businessExceptionOf("flow.web_oauth2_provider.missing_code")
        }
        val provider = providerConfigManager.findByIdAndCheckEnabled(providerId)
        val oauth2 = getOauth2(provider)
        val authentication = fetchTokens(provider, oauth2, authorizeCode)

        val rawUserInfo = providerClaimsManager.fetchUserInfo(provider, authentication)

        val existingUserInfo = providerClaimsManager.findByProviderAndSubject(
            provider = provider,
            subject = rawUserInfo.subject
        )

        val user = if (existingUserInfo == null) {
            createOrAssociateUserWithProviderUserInfo(provider, rawUserInfo).user
        } else {
            providerClaimsManager.refreshUserInfo(existingUserInfo, rawUserInfo)
            existingUserInfo.userId
            TODO("FIXME")
        }
        val updatedAuthorizeAttempt = authorizeAttemptManager.setAuthenticatedUserId(authorizeAttempt, user.id)

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
        return if (authConfig.userMergingEnabled) {
            createOrAssociateUserByIdentifierClaimsWithProviderUserInfo(authConfig, provider, providerUserInfo)
        } else {
            createUserWithProviderUserInfo(provider, providerUserInfo)
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
        authConfig: EnabledAuthConfig,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val identifierClaims = authConfig.identifierClaims

        // Extract identifier claim values from provider user info.
        val claimValues = identifierClaims.associateWith { claim ->
            providerUserInfo.getClaimValueOrNull(claim)
                ?: throw businessExceptionOf(
                    "user.create_with_provider.missing_identifier_claim",
                    "providerId" to provider.id,
                    "claim" to claim.id
                )
        }

        // Resolve the Claim business objects for each identifier claim.
        val claimObjects = identifierClaims.associateWith { claim ->
            claimManager.findByIdOrNull(claim.id)
                ?: throw businessExceptionOf(
                    "user.create_with_provider.missing_identifier_claim_config",
                    "claim" to claim.id
                )
        }

        // Look up existing user matching ALL identifier claims.
        val identifierMap = claimValues.map { (claim, value) -> claim.id to value }.toMap()
        val existingUser = userManager.findByIdentifierClaims(identifierMap)

        val user = existingUser ?: userManager.createUser().also { newUser ->
            collectedClaimManager.update(
                user = newUser,
                updates = claimValues.map { (claim, value) ->
                    CollectedClaimUpdate(
                        claim = claimObjects.getValue(claim),
                        value = Optional.of(value)
                    )
                }
            )
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

    @Transactional
    internal open suspend fun createUserWithProviderUserInfo(
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        TODO("FIXME")
    }

    suspend fun fetchTokens(
        provider: Provider,
        oauth2: ProviderOauth2Config,
        authorizeCode: String
    ): ProviderOauth2Tokens {
        val request = ProviderOAuth2TokenRequest(
            oauth2 = oauth2,
            authorizeCode = authorizeCode,
            redirectUri = getRedirectUri(provider)
        )
        val tokens = tokenEndpointClient.fetchTokens(request)
        return ProviderOauth2Tokens(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken
        )
    }
}
