package com.sympauthy.business.manager.flow

import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_CALLBACK_ENDPOINT
import com.sympauthy.api.controller.flow.ProvidersController.Companion.FLOW_PROVIDER_ENDPOINTS
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.auth.AuthorizeAttemptManager
import com.sympauthy.business.manager.flow.reauth.ReAuthenticationCompletionDispatcher
import com.sympauthy.business.manager.flow.reauth.WebAuthorizationFlowProviderAttachManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderClaimsResolver
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.reauth.ReAuthenticationManager
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.reauth.ReAuthenticationMethod
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
import org.slf4j.LoggerFactory
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
    @Inject private val clientManager: ClientManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val providerAttachManager: WebAuthorizationFlowProviderAttachManager,
    @Inject private val providerConfigManager: ProviderManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val providerClaimsResolver: ProviderClaimsResolver,
    @Inject private val reAuthenticationManager: ReAuthenticationManager,
    @Inject private val reAuthenticationCompletionDispatcher: ReAuthenticationCompletionDispatcher,
    @Inject private val webAuthorizationFlowManager: WebAuthorizationFlowManager,
    @Inject private val tokenEndpointClient: TokenEndpointClient,
    @Inject private val userManager: UserManager,
    @Inject private val uncheckedAuthConfig: AuthConfig,
    @Inject private val uncheckedUrlsConfig: UrlsConfig
) {

    private val logger = LoggerFactory.getLogger(WebAuthorizationFlowOAuth2ProviderManager::class.java)

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
     * - sign in the user if the provider subject is already known.
     * - otherwise, sign up the user with the claims retrieved from the [Provider], rejecting the sign-in if the
     *   identifier claims already match an existing account.
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

        // If a re-authentication (interactive provider attach) is pending, this callback is the end-user proving
        // ownership of the target account with an already-linked provider — not a normal sign-in.
        if (authorizeAttempt.reauthenticationAttemptId != null) {
            val updated = completeProviderReAuthentication(authorizeAttempt, provider, rawUserInfo)
            return webAuthorizationFlowManager.getStatusAndCompleteIfNecessary(updated)
        }

        val existingUserInfo = providerClaimsManager.findByProviderAndSubject(
            provider = provider,
            subject = rawUserInfo.subject
        )

        val updatedAuthorizeAttempt = if (existingUserInfo != null) {
            // Known provider subject: sign the user in.
            providerClaimsManager.refreshUserInfo(existingUserInfo, rawUserInfo)
            authorizeAttemptManager.setAuthenticatedUserId(authorizeAttempt, existingUserInfo.userId)
        } else {
            signUpOrStartAttach(authorizeAttempt, provider, rawUserInfo)
        }

        return webAuthorizationFlowManager.getStatusAndCompleteIfNecessary(
            authorizeAttempt = updatedAuthorizeAttempt
        )
    }

    /**
     * Complete a pending re-authentication (interactive provider attach) when the end-user re-authenticated through
     * an already-linked provider. The resolved provider subject must map to the account being confirmed
     * ([com.sympauthy.business.model.reauth.ReAuthenticationAttempt.targetUserId]).
     *
     * On success the ownership proof is recorded and handed off to the purpose handler (which promotes the pending
     * provider identity). If the end-user re-authenticated with the wrong provider account — or the
     * re-authentication has expired — the attach stays pending and the end-user is routed back to the sign-in page
     * to try again.
     */
    @Transactional
    internal open suspend fun completeProviderReAuthentication(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): AuthorizeAttempt {
        val reAuthenticationId = authorizeAttempt.reauthenticationAttemptId ?: return authorizeAttempt
        val reAuthentication = reAuthenticationManager.getPendingOrNull(reAuthenticationId)
            ?: return authorizeAttempt

        val existingUserInfo = providerClaimsManager.findByProviderAndSubject(provider, providerUserInfo.subject)
        if (existingUserInfo == null || existingUserInfo.userId != reAuthentication.targetUserId) {
            logger.warn(
                "Provider re-authentication failed: provider={} subject resolved to userId={} but target was {} (attemptId={})",
                provider.id, existingUserInfo?.userId, reAuthentication.targetUserId, authorizeAttempt.id
            )
            return authorizeAttempt
        }

        providerClaimsManager.refreshUserInfo(existingUserInfo, providerUserInfo)
        val passed = reAuthenticationManager.markPassed(reAuthentication, ReAuthenticationMethod.PROVIDER)
        return reAuthenticationCompletionDispatcher.complete(authorizeAttempt, passed)
    }

    /**
     * Handle a provider sign-in whose subject is unknown:
     * - if no account matches the identifier claims, sign up a brand-new account and authenticate it.
     * - if an account already matches the identifier claims (a collision) and the client's audience allows it,
     *   start the interactive attach + forced re-login flow instead of authenticating. The provider identity is
     *   NOT silently merged into the existing account (that was an account-takeover vector).
     * - otherwise (attach disabled, or the account already has this provider linked) reject: the end-user must
     *   sign in with their existing account.
     *
     * Returns the updated [OnGoingAuthorizeAttempt] (authenticated for a new account, or with a pending
     * re-authentication when an attach was started).
     */
    @Transactional
    internal open suspend fun signUpOrStartAttach(
        authorizeAttempt: OnGoingAuthorizeAttempt,
        provider: EnabledProvider,
        providerUserInfo: RawProviderClaims
    ): OnGoingAuthorizeAttempt {
        val authConfig = uncheckedAuthConfig.orThrow()
        val identifierClaims = resolveIdentifierClaims(authConfig, provider, providerUserInfo)
        val identifierMap = identifierClaims.map { (claimId, pair) -> claimId to pair.second }.toMap()
        val existingUser = userManager.findByIdentifierClaims(identifierMap)

        if (existingUser == null) {
            webAuthorizationFlowManager.checkSignUpAllowed(authorizeAttempt, recoverable = false)
            val user = userManager.createUser()
            saveIdentifierClaims(user, identifierClaims)
            providerClaimsManager.saveUserInfo(
                provider = provider,
                userId = user.id,
                rawProviderClaims = providerUserInfo
            )
            invitationManager.applyInvitationClaimsAndConsume(authorizeAttempt.invitationId, user.id)
            return authorizeAttemptManager.setAuthenticatedUserId(authorizeAttempt, user.id)
        }

        // Collision: an account already exists with the same identifier claims.
        val audience = clientManager.findClientById(authorizeAttempt.clientId).audience
        if (!audience.providerAttachEnabled) {
            throw businessExceptionOf("user.create_with_provider.existing_user")
        }
        // A user can hold at most one identity per provider, so a target already linked to this provider cannot
        // receive a second one — reject rather than attempt an impossible attach.
        if (providerClaimsManager.findByUserIdAndProviderIdOrNull(existingUser.id, provider.id) != null) {
            throw businessExceptionOf("user.create_with_provider.existing_user")
        }
        return providerAttachManager.startAttach(authorizeAttempt, existingUser, provider, providerUserInfo)
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
