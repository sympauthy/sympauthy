package com.sympauthy.business.manager.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_CALLBACK_ENDPOINT
import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.CreateOrAssociateResult
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.WebAuthorizationFlowStatus
import com.sympauthy.business.model.oauth2.AuthorizeAttempt
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.Provider
import com.sympauthy.business.model.provider.config.ProviderOauth2Config
import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2TokenRequest
import com.sympauthy.business.model.provider.oauth2.ProviderOauth2Tokens
import com.sympauthy.business.model.redirect.ProviderOauth2AuthorizationRedirect
import com.sympauthy.business.model.user.CollectedClaimUpdate
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.business.model.user.UserMergingStrategy
import com.sympauthy.business.model.user.claim.OpenIdClaim
import com.sympauthy.client.oauth2.TokenEndpointClient
import com.sympauthy.config.model.AdvancedConfig
import com.sympauthy.config.model.UrlsConfig
import com.sympauthy.config.model.getUri
import com.sympauthy.config.model.orThrow
import io.micronaut.http.HttpResponse
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
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val tokenEndpointClient: TokenEndpointClient,
    @Inject private val userManager: UserManager,
    @Inject private val uncheckedAdvancedConfig: AdvancedConfig,
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

    suspend fun authorizeWithProvider(
        authorizeAttempt: AuthorizeAttempt,
        provider: EnabledProvider
    ): HttpResponse<*> {
        val oauth2 = getOauth2(provider)
        val redirectUri = ProviderOauth2AuthorizationRedirect(
            oauth2 = oauth2,
            responseType = "code",
            redirectUri = getRedirectUri(provider),
            state = authorizeAttemptManager.encodeState(authorizeAttempt)
        ).build()
        return HttpResponse.temporaryRedirect<Any>(redirectUri)
    }

    /**
     * Finalize the sign-in or sign-up of the end-user that authenticated through the [provider].
     *
     * This method will perform the following actions:
     * - retrieve the access token from the [provider] to check if the user is authenticated.
     * - retrieve end-user claims from the [provider] and store them in this authorization server database.
     * - sign-in the user if it already exists according to user-merging strategy.
     * - otherwise, sign-up the user with the claims retrieved from the [provider].
     */
    suspend fun signInOrSignUpUsingProvider(
        authorizeAttempt: AuthorizeAttempt,
        provider: EnabledProvider,
        authorizeCode: String
    ): WebAuthorizationFlowStatus {
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

        return webAuthorizationFlowManager.completeAuthorizationIfNecessaryAndGetStatus(
            authorizeAttempt = updatedAuthorizeAttempt
        )
    }

    /**
     * Create a new [com.sympauthy.business.model.user.User] or associate to an existing [com.sympauthy.business.model.user.User].
     * Then update the provider user info with the newly collected [providerUserInfo].
     *
     * Depending on the ```advanced.user-merging-strategy```, we may instead associate the [providerUserInfo] to
     * an existing user.
     */
    @Transactional
    open suspend fun createOrAssociateUserWithProviderUserInfo(
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        return when (uncheckedAdvancedConfig.orThrow().userMergingStrategy) {
            UserMergingStrategy.BY_MAIL -> createOrAssociateUserByEmailWithProviderUserInfo(provider, providerUserInfo)
            UserMergingStrategy.NONE -> createUserWithProviderUserInfo(provider, providerUserInfo)
        }
    }

    /**
     * Create a new [com.sympauthy.business.model.user.User] or associate it to a [com.sympauthy.business.model.user.User] that have the same email.
     * and update the user info collected by the [provider] with the [providerUserInfo].
     *
     * If the ```advanced.user-merging-strategy``` is set to ```by-mail```, we will check if we have an existing user
     * with the email first. If yes, we will only update the user info, otherwise, we will create it.
     *
     * The email is collected and copied as a first party data. We want this information to be stable
     * and not be affected by changes from the third party in the future.
     * Otherwise, an update from a provider may break our uniqueness and cause uncontrolled side effects.
     */
    @Transactional
    internal open suspend fun createOrAssociateUserByEmailWithProviderUserInfo(
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): CreateOrAssociateResult {
        val email = providerUserInfo.email ?: throw businessExceptionOf(
            "user.create_with_provider.missing_email",
            values = arrayOf("providerId" to provider.id)
        )
        val emailClaim = claimManager.findById(OpenIdClaim.Id.EMAIL) ?: throw businessExceptionOf(
            "user.create_with_provider.missing_email_claim"
        )
        val existingUser = userManager.findByEmail(email)
        val user = existingUser
            ?: userManager.createUser().apply {
                // We do not pass the scopes to forcefully update since we must save the email even if it was not requested by the client.
                collectedClaimManager.update(
                    user = this,
                    updates = listOf(
                        CollectedClaimUpdate(
                            claim = emailClaim,
                            value = Optional.of(email)
                        )
                    )
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
