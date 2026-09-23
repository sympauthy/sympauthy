package com.sympauthy.business.manager.flow

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.flow.link.InteractiveFlowSessionLinkProviderManager
import com.sympauthy.business.manager.flow.reauth.InteractiveFlowSessionReauthenticationManager
import com.sympauthy.business.manager.lock.LockKey
import com.sympauthy.business.manager.lock.LockManager
import com.sympauthy.business.manager.security.UserSecurityContextManager
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.provider.ProviderClaimsResolver
import com.sympauthy.business.manager.provider.ProviderManager
import com.sympauthy.business.manager.user.CollectedClaimManager
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
 * It is purpose-agnostic: nothing here reads which purpose is driving the session, so a purpose needing a
 * provider round-trip uses it unmodified. The "establish a new user" outcome, which is
 * [InteractiveFlowPurpose.OAUTH2_AUTHORIZE]'s alone, is delegated to the [ProviderUserEstablisher] seam so
 * this manager stays free of identity logic belonging to one purpose.
 */
@Singleton
open class InteractiveFlowSessionOAuth2ProviderManager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val providerManager: InteractiveFlowSessionProviderManager,
    @Inject private val reauthenticationManager: InteractiveFlowSessionReauthenticationManager,
    @Inject private val userSecurityContextManager: UserSecurityContextManager,
    @Inject private val providerConfigManager: ProviderManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager,
    @Inject private val providerClaimsResolver: ProviderClaimsResolver,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val tokenEndpointClient: TokenEndpointClient,
    @Inject private val establisher: ProviderUserEstablisher,
    @Inject private val linkProviderManager: InteractiveFlowSessionLinkProviderManager,
    @Inject private val userManager: UserManager,
    @Inject private val claimManager: ClaimManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val lockManager: LockManager,
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
        observedRequest: ObservedRequest,
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
                    confirmReauthenticatedProviderUser(session, existingUserInfo, rawUserInfo, observedRequest)
                InteractiveFlowPurpose.LINK_PROVIDER ->
                    linkProviderToSessionUser(session, provider, rawUserInfo)
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
        // Stamped before the flow advances: completing it is what folds the observation into the person's
        // record. The provider round-trip landed in their own browser, so this is their address.
        userSecurityContextManager.markProven(updatedSession.id, observedRequest)

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
        rawUserInfo: RawProviderClaims,
        observedRequest: ObservedRequest
    ): InteractiveFlowSession {
        if (existingUserInfo == null || existingUserInfo.userId != session.userId) {
            throw recoverableBusinessExceptionOf(
                detailsId = "flow.reauthentication.provider_not_linked",
                descriptionId = "description.flow.reauthentication.provider_not_linked"
            )
        }
        // After the check above: until then the round-trip proves an account, not this session's.
        userSecurityContextManager.markProven(session.id, observedRequest)
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
     * - an identifier value the provider asserts is already owned by **another** account, under any of the
     *   configured identifier claims.
     *
     * A subject already linked to **this** user is an idempotent success (the stored claims are refreshed).
     *
     * Every one of those answers is [linkSubjectToUser]'s, under the lock, from a read of its own. The
     * subject the callback resolved before this says only what was committed then, and a branch on it here
     * would be the copy of the decision that nothing serialises.
     */
    private suspend fun linkProviderToSessionUser(
        session: OnGoingInteractiveFlowSession,
        provider: EnabledProvider,
        rawUserInfo: RawProviderClaims
    ): InteractiveFlowSession {
        val userId = session.userId
            ?: throw businessExceptionOf("flow.link_provider.missing_user")

        linkSubjectToUser(provider, userId, rawUserInfo)
        return engine.completeIfNecessary(session)
    }

    /**
     * Write the committed link from [provider]'s [rawUserInfo] to [userId], under the lock over every
     * identity it names.
     *
     * The subject read at the top of the callback says what was committed then, and this is a writer of
     * exactly that — as are the promotion of a provisional account and the establisher merging a provider
     * into an existing one. So the read is taken again here, under the key all of them name: without it two
     * of these each find the subject free and both link it, and `provider_user_info` keys on
     * `(provider_id, user_id)` and stops neither. See [LockKey.ProviderSubject].
     *
     * The identifier values the provider asserts are named in the same call, because the conflict below
     * compares on them. A promotion is what commits an account holding one, and it holds
     * [LockKey.IdentifierValue] over that value rather than over this subject: locking the subject alone
     * excludes it from nothing, so the link reads no owner, the promotion commits, and what is left is the
     * end state `flow.link_provider.identifier_conflict` exists to refuse. One call naming both, since a
     * second one in this transaction is refused.
     *
     * Advancing the flow stays outside: completing takes a lock of its own, and a transaction still
     * holding this one would refuse it as a second lock naming keys this one does not hold.
     */
    private suspend fun linkSubjectToUser(
        provider: EnabledProvider,
        userId: UUID,
        rawUserInfo: RawProviderClaims
    ) {
        val asserted = getAssertedIdentifiersOrNull(rawUserInfo)
        val keys = listOf(LockKey.ProviderSubject(provider.id, rawUserInfo.subject)) + asserted?.lockKeys.orEmpty()

        lockManager.withLock(*keys.toTypedArray()) {
            val committed = providerClaimsManager.findByProviderAndSubject(provider, rawUserInfo.subject)
            if (committed != null) {
                if (committed.userId == userId) {
                    // Linked to this user while this callback was in flight — the outcome it wanted.
                    providerClaimsManager.refreshUserInfo(committed, rawUserInfo)
                    return@withLock
                }
                throw businessExceptionOf(
                    "flow.link_provider.subject_conflict",
                    "providerId" to provider.id
                )
            }

            val taken = asserted?.let {
                userManager.findTakenIdentifierOrNull(userId, it.claimIds, it.valuesByClaimId)
            }
            if (taken != null) {
                // The claim the provider asserted it under, never the one the other account holds it under:
                // the first is what this callback already sent, and the second would say something about an
                // account the person linking is not entitled to hear about. The owner travels beside it for
                // the operator's half of the message, which no deployment prints unless it asks to.
                throw businessExceptionOf(
                    "flow.link_provider.identifier_conflict",
                    "providerId" to provider.id,
                    "claim" to taken.claimId,
                    "userId" to "${taken.userId}"
                )
            }

            // A permanent link, never a provisional one: the account was checked promoted before this session
            // was created (InteractiveFlowSessionLinkProviderManager.startLinkProviderSession), and promotion
            // is one-way, so its session id is null and reading it back would only re-answer that.
            providerClaimsManager.saveUserInfo(provider, userId, sessionId = null, rawUserInfo)
            logger.info(
                "Linked provider {} (subject {}) to user {}.",
                provider.id,
                rawUserInfo.subject,
                userId
            )
        }
    }

    /**
     * The identifier claim values [rawUserInfo] asserts, or null when there is no conflict to evaluate at
     * all — no identifier claim is configured, or the provider asserts none of them — in which case the
     * subject check remains the primary defense.
     *
     * **Whatever subset the provider does assert is the subset checked**, rather than all of them or
     * nothing. Each value stands on its own under the rule
     * ([UserManager.findTakenIdentifierOrNull]): a committed account holding one of them already
     * owns the identity this provider is asserting, whichever of the others it is silent about. Demanding
     * every configured claim would leave the check dead in the ordinary deployment — `[email,
     * phone_number]` against a provider that carries an address and no number.
     *
     * The values the check compares and the keys locking them come out of this one read, and null answers
     * for both: a key over a check that does not run excludes a promotion for nothing, and a check with no
     * key over it is the one this answer exists to keep out. An asserted value is spelled by
     * [CollectedClaimManager.getStoredValueOf], which cleans it under the claim asserting it first, because
     * the check compares on the spelling a collected value was stored in — an address a provider
     * capitalises is otherwise a conflict nothing sees. A value that claim could hold no such value of
     * drops out with it: neither half of the pair can be formed, and no row it would have matched exists.
     */
    private suspend fun getAssertedIdentifiersOrNull(rawUserInfo: RawProviderClaims): AssertedIdentifiers? {
        val valuesByClaimId = claimManager.listIdentifierClaims().mapNotNull { claim ->
            rawUserInfo.getClaimValueOrNull(claim)
                ?.let { collectedClaimManager.getStoredValueOf(claim, it) }
                ?.let { claim.id to it }
        }.toMap()
        if (valuesByClaimId.isEmpty()) return null
        return AssertedIdentifiers(
            claimIds = uncheckedAuthConfig.orThrow().identifierClaims,
            valuesByClaimId = valuesByClaimId
        )
    }

    /**
     * The identifier claim values a provider asserts, as `collected_claims` spells them — the one spelling
     * the link needs them in: [UserManager.findTakenIdentifierOrNull] compares committed rows on it,
     * and [LockKey.IdentifierValue] names the same value the promotion racing this one locks under.
     *
     * [claimIds] is every identifier claim this deployment configured, not only the ones asserted: the
     * conflict is any of these values under any of them, and an account holding one under a claim the
     * provider is silent about owns it just the same.
     */
    private class AssertedIdentifiers(
        val claimIds: List<String>,
        val valuesByClaimId: Map<String, String>
    ) {
        val lockKeys: List<LockKey> = valuesByClaimId.values.map(LockKey::IdentifierValue)
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
