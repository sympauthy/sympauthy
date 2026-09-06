package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.flow.link.InteractiveFlowSessionLinkProviderManager
import com.sympauthy.business.manager.flow.reauth.InteractiveFlowSessionReauthenticationManager
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderClaimsResolver
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.provider.EnabledProvider
import com.sympauthy.business.model.provider.Provider
import com.sympauthy.business.model.provider.ProviderUserInfo
import com.sympauthy.business.model.provider.config.ProviderAuthConfig
import com.sympauthy.business.model.provider.config.ProviderOAuth2Config
import com.sympauthy.business.model.provider.config.ProviderOpenIdConnectConfig
import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2TokenRequest
import com.sympauthy.business.model.provider.oauth2.ProviderOAuth2Tokens
import com.sympauthy.business.model.redirect.ProviderOAuth2AuthorizationRedirect
import com.sympauthy.business.model.redirect.ProviderOpenIdConnectAuthorizationRedirect
import com.sympauthy.business.model.user.RawProviderClaims
import com.sympauthy.client.oauth2.TokenEndpointClient
import com.sympauthy.config.model.*
import com.sympauthy.util.loggerForClass
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.UUID

/**
 * Manager owning the interaction with a third-party OAuth 2 / OpenID Connect provider within an interactive
 * flow session: it drives the end-user through the provider's authorization, handles the callback, exchanges
 * the code for tokens and resolves the end-user claims, then routes the outcome by the session's current
 * purpose.
 *
 * It is purpose-agnostic and shared by every consumer that authorizes with a provider — sign-in / sign-up
 * ([InteractiveFlowPurpose.OAUTH2_AUTHORIZE]), ownership proof
 * ([InteractiveFlowPurpose.REAUTHENTICATION]) and provider linking
 * ([InteractiveFlowPurpose.LINK_PROVIDER]). The OAuth2-authorize-specific "establish a new user" outcome is
 * delegated to the [ProviderUserEstablisher] seam so this manager stays free of consumer-specific identity
 * logic.
 */
@Singleton
open class InteractiveFlowSessionOAuth2ProviderManager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val providerManager: InteractiveFlowSessionProviderManager,
    @Inject private val reauthenticationManager: InteractiveFlowSessionReauthenticationManager,
    @Inject private val providerConfigManager: ProviderManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val providerClaimsResolver: ProviderClaimsResolver,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val tokenEndpointClient: TokenEndpointClient,
    @Inject private val establisher: ProviderUserEstablisher,
    @Inject private val linkProviderManager: InteractiveFlowSessionLinkProviderManager,
    @Inject private val userManager: UserManager,
    @Inject private val uncheckedAuthConfig: AuthConfig
) {

    private val logger = loggerForClass()

    fun getOAuth2(provider: EnabledProvider): ProviderOAuth2Config {
        if (provider.auth !is ProviderOAuth2Config) {
            throw businessExceptionOf("provider.oauth2.unsupported")
        }
        return provider.auth
    }

    /**
     * Return the URL to redirect the end-user to start the authorization process with the [Provider] identified by
     * [providerId], which sends them back to [redirectUri] once they have authenticated.
     *
     * If the provider is not found or is disabled, an unrecoverable business exception is thrown.
     *
     * The very same [redirectUri] must later be given to [signInOrSignUpUsingProvider], since RFC 6749
     * section 4.1.3 requires the token request to repeat the redirect uri of the authorization request.
     */
    suspend fun authorizeWithProvider(
        session: OnGoingInteractiveFlowSession,
        providerId: String,
        redirectUri: URI
    ): URI {
        val provider = providerConfigManager.findByIdAndCheckEnabled(providerId)
        // Once the session user is fixed, a provider round-trip may only re-confirm ownership with a provider
        // already linked to that user — never establish a new identity (sign-in, which establishes, always
        // runs with userId == null). The one exception is the LINK_PROVIDER purpose, which authorizes a
        // not-yet-linked provider: there we instead require the provider to be exactly the link intent, so a
        // stolen link-session state cannot authorize an arbitrary provider.
        if (session.userId != null) {
            if (engine.currentPurposeOrNull(session) == InteractiveFlowPurpose.LINK_PROVIDER) {
                requireProviderIsLinkTarget(session, providerId)
            } else {
                requireProviderLinkedToSessionUser(session, providerId)
            }
        }
        val state = sessionManager.encodeState(session)
        return when (val auth = provider.auth) {
            is ProviderOpenIdConnectConfig -> {
                val nonce = providerManager.setProvider(session, providerId, generateNonce = true)!!
                ProviderOpenIdConnectAuthorizationRedirect(
                    openIdConnect = auth,
                    responseType = "code",
                    redirectUri = redirectUri,
                    state = state,
                    nonce = nonce
                ).build()
            }

            is ProviderOAuth2Config -> {
                providerManager.setProvider(session, providerId)
                ProviderOAuth2AuthorizationRedirect(
                    oauth2 = auth,
                    responseType = "code",
                    redirectUri = redirectUri,
                    state = state
                ).build()
            }
        }
    }

    /**
     * Finalize the sign-in or sign-up of the end-user that authenticated through the [Provider] identified by
     * [providerId].
     *
     * This method will perform the following actions:
     * - retrieve the access token from the [Provider] to check if the user is authenticated.
     * - retrieve end-user claims from the [Provider] and store them in this authorization server database.
     * - sign in the user if it already exists, according to the user-merging strategy.
     * - otherwise, sign up the user with the claims retrieved from the [Provider].
     *
     * [redirectUri] is the one given to [authorizeWithProvider] when this flow was started: RFC 6749 section
     * 4.1.3 requires the token request to repeat it, and a provider rejects the exchange when it differs.
     */
    suspend fun signInOrSignUpUsingProvider(
        session: OnGoingInteractiveFlowSession,
        providerId: String?,
        redirectUri: URI,
        authorizeCode: String?,
        providerError: String? = null,
        providerErrorDescription: String? = null
    ): InteractiveFlowSession {
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
        // Fetch the provider record once and reuse it for the nonce reconstruction below.
        val sessionProvider = providerManager.fetchProviderOrNull(session)
        val storedProviderId = sessionProvider?.providerId
        if (storedProviderId != null && storedProviderId != providerId) {
            throw businessExceptionOf(
                "flow.web_oauth2_provider.provider_mismatch",
                "expectedProviderId" to storedProviderId,
                "actualProviderId" to (providerId ?: "")
            )
        }

        val provider = providerConfigManager.findByIdAndCheckEnabled(providerId)

        val tokens = fetchTokens(provider, provider.auth, authorizeCode, redirectUri)
        val expectedNonce = providerManager.buildProviderNonceOrNull(sessionProvider)
        val rawUserInfo = providerClaimsResolver.resolveClaims(provider, tokens, expectedNonce)

        val existingUserInfo = providerClaimsManager.findByProviderAndSubject(
            provider = provider,
            subject = rawUserInfo.subject
        )

        // Never switch an already-fixed session user via a provider round-trip:
        // - user already fixed under a re-authentication gate -> CONFIRM the resolved account is that user.
        // - user already fixed under a provider-link gate -> LINK the resolved provider to the fixed user.
        // - user already fixed but a later purpose is now active -> provider sign-in no longer applies; return
        //   the session unchanged so the flow redirects to its current step, without switching identity.
        // Sign-in/up, which establishes identity below, only runs while userId == null.
        if (session.userId != null) {
            return when (engine.currentPurposeOrNull(session)) {
                InteractiveFlowPurpose.REAUTHENTICATION ->
                    confirmReauthenticatedProviderUser(session, existingUserInfo, rawUserInfo)
                InteractiveFlowPurpose.LINK_PROVIDER ->
                    linkProviderToSessionUser(session, provider, existingUserInfo, rawUserInfo)
                else -> session
            }
        }

        val (userId, signedUp) = if (existingUserInfo == null) {
            val establishment = establisher.establishNewProviderUser(session, provider, rawUserInfo)
            establishment.userId to establishment.signedUp
        } else {
            providerClaimsManager.refreshUserInfo(existingUserInfo, rawUserInfo)
            existingUserInfo.userId to false
        }
        val updatedSession = sessionManager.setAuthenticatedUserId(session, userId, signedUp = signedUp)

        // Complete the flow if the end-user has no more step to go through.
        return engine.completeIfNecessary(updatedSession)
    }

    /**
     * Reject with a recoverable error unless the [providerId] is a provider already linked to the session's
     * fixed [OnGoingInteractiveFlowSession.userId]. Guards the authorize redirect during re-authentication so
     * only the account's own providers can be used to prove ownership.
     */
    private suspend fun requireProviderLinkedToSessionUser(
        session: OnGoingInteractiveFlowSession,
        providerId: String
    ) {
        val userId = session.userId
        if (userId == null ||
            providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, providerId) == null
        ) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.reauthentication.provider_not_linked",
                descriptionId = "description.flow.reauthentication.provider_not_linked"
            )
        }
    }

    /**
     * Re-authentication path: the provider account just proven must be already linked to the session's fixed
     * [OnGoingInteractiveFlowSession.userId] — **confirm, never establish**. On a match, refresh the stored
     * claims and record the primary credential as proven **without** touching the session's user id, then
     * advance. If the subject is unknown or linked to a different user, reject with a recoverable error (retry
     * on the sign-in step); the session user is never switched.
     */
    private suspend fun confirmReauthenticatedProviderUser(
        session: OnGoingInteractiveFlowSession,
        existingUserInfo: ProviderUserInfo?,
        rawUserInfo: RawProviderClaims
    ): InteractiveFlowSession {
        if (existingUserInfo == null || existingUserInfo.userId != session.userId) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.reauthentication.provider_not_linked",
                descriptionId = "description.flow.reauthentication.provider_not_linked"
            )
        }
        providerClaimsManager.refreshUserInfo(existingUserInfo, rawUserInfo)
        reauthenticationManager.markPrimaryCredentialProven(session)
        return engine.completeIfNecessary(session)
    }

    /**
     * Reject with a recoverable error unless [providerId] is exactly the provider the LINK_PROVIDER session was
     * created to link (its link-provider record). This enforces the link intent so a leaked link-session state
     * cannot be used to authorize — and then link — an arbitrary provider the attacker controls.
     */
    private suspend fun requireProviderIsLinkTarget(
        session: OnGoingInteractiveFlowSession,
        providerId: String
    ) {
        val targetProviderId = linkProviderManager.fetchLinkProviderOrNull(session)?.providerId
        if (targetProviderId != providerId) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.link_provider.wrong_provider",
                descriptionId = "description.flow.link_provider.wrong_provider"
            )
        }
    }

    /**
     * Provider-link path: link the resolved provider account to the session's **fixed**
     * [OnGoingInteractiveFlowSession.userId], then advance. The session user is never switched — linking only
     * ever attaches a provider to the already-identified account.
     *
     * Conflicts hard-fail (unrecoverable, so the flow fails and nothing is linked):
     * - the provider subject is already linked to **another** account (an identity cannot belong to two users);
     * - an identifier claim the provider asserts is already owned by **another** account.
     *
     * A subject already linked to **this** user is an idempotent success (the stored claims are refreshed).
     */
    private suspend fun linkProviderToSessionUser(
        session: OnGoingInteractiveFlowSession,
        provider: EnabledProvider,
        existingUserInfo: ProviderUserInfo?,
        rawUserInfo: RawProviderClaims
    ): InteractiveFlowSession {
        val userId = session.userId
            ?: throw businessExceptionOf("flow.link_provider.missing_user")

        if (existingUserInfo != null) {
            if (existingUserInfo.userId == userId) {
                // Already linked to this user: idempotent success.
                providerClaimsManager.refreshUserInfo(existingUserInfo, rawUserInfo)
                return engine.completeIfNecessary(session)
            }
            // Linked to a different account: an identity cannot belong to two accounts.
            throw businessExceptionOf(
                "flow.link_provider.subject_conflict",
                "providerId" to provider.id
            )
        }

        val identifierOwnerId = findUserOwningIdentifierClaimsOrNull(provider, rawUserInfo)
        if (identifierOwnerId != null && identifierOwnerId != userId) {
            throw businessExceptionOf(
                "flow.link_provider.identifier_conflict",
                "providerId" to provider.id
            )
        }

        // The link takes the session id of the account it attaches to — under this purpose always an
        // already-committed one — rather than the session serving the request.
        val linkedUser = userManager.findById(userId)
        providerClaimsManager.saveUserInfo(provider, userId, linkedUser.sessionId, rawUserInfo)
        logger.info(
            "Linked provider {} (subject {}) to user {}.",
            provider.id,
            rawUserInfo.subject,
            userId
        )
        return engine.completeIfNecessary(session)
    }

    /**
     * Return the id of a user that already owns the identifier claim values [provider] asserts in
     * [rawUserInfo], or null when there is none — or when the conflict cannot be evaluated (no identifier
     * claims configured, or the provider omits one), in which case the subject check remains the primary
     * defense. Only the identifier **values** are needed here (not the resolved claim objects), so this is a
     * plain lookup rather than the full sign-up claim resolution.
     */
    private suspend fun findUserOwningIdentifierClaimsOrNull(
        provider: EnabledProvider,
        rawUserInfo: RawProviderClaims
    ): UUID? {
        val identifierClaims = uncheckedAuthConfig.orThrow().identifierClaims
        if (identifierClaims.isEmpty()) return null
        val identifierValues = identifierClaims.associateWith { claimId ->
            rawUserInfo.getClaimValueOrNull(claimId) ?: return null
        }
        return userManager.findByIdentifierClaims(identifierValues)?.id
    }

    suspend fun fetchTokens(
        provider: Provider,
        auth: ProviderAuthConfig,
        authorizeCode: String,
        redirectUri: URI
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
            redirectUri = redirectUri
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
