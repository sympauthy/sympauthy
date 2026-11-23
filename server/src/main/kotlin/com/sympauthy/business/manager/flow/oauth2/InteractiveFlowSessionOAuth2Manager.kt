package com.sympauthy.business.manager.flow.oauth2

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.client.ClientManager
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.mapper.InteractiveFlowSessionOAuth2Mapper
import com.sympauthy.business.model.audience.Audience
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlowHandBack
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.user.claim.RequestedClaims
import com.sympauthy.data.model.InteractiveFlowSessionOAuth2Entity
import com.sympauthy.data.repository.InteractiveFlowSessionOAuth2Repository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.time.LocalDateTime
import java.util.*

/**
 * Manager in charge of the OAuth2 request context attached to an [InteractiveFlowSession].
 * It provides methods to:
 * - start an OAuth2 session (the session + its attached OAuth2 record, atomically).
 * - fetch the OAuth2 record of a session.
 * - modify the OAuth2 record: consented scopes, granted scopes.
 *
 * It builds on top of [InteractiveFlowSessionManager] for the flow-generic session lifecycle.
 */
@Singleton
open class InteractiveFlowSessionOAuth2Manager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val oauth2Repository: InteractiveFlowSessionOAuth2Repository,
    @Inject private val oauth2Mapper: InteractiveFlowSessionOAuth2Mapper,
) {

    /**
     * Create a new OAuth2 [InteractiveFlowSession] for the end-user and save it in the database, together
     * with its attached [InteractiveFlowSessionOAuth2Entity] record, in a single transaction.
     *
     * The session is saved even if the validation of some parameters failed (i.e. [error] is non-null), and
     * [requestError] is then the error answering that refusal on the client's redirect URI.
     *
     * The session and its OAuth2 record are written together, so a failure saving the record leaves no session
     * behind naming an initiating client that no record backs — one no state was ever handed out for, which the
     * admin surface would list until it expired.
     */
    @Transactional
    open suspend fun startOAuth2Session(
        client: Client? = null,
        clientState: String? = null,
        clientNonce: String? = null,
        flow: AuthorizationFlow? = null,
        scopes: List<EnabledScope>? = null,
        requestedClaims: RequestedClaims = RequestedClaims.NONE,
        redirectUri: URI? = null,
        codeChallenge: String? = null,
        codeChallengeMethod: CodeChallengeMethod? = null,
        invitationId: UUID? = null,
        error: BusinessException? = null,
        requestError: OAuth2ErrorCode? = null
    ): InteractiveFlowSession {
        val now = LocalDateTime.now()
        // The client redirect URI is where every ending goes: the code on completion, the OAuth2 error otherwise.
        val session = sessionManager.newSession(
            purposes = listOf(InteractiveFlowPurpose.OAUTH2_AUTHORIZE),
            initiatingPurpose = InteractiveFlowPurpose.OAUTH2_AUTHORIZE,
            initiatingClientId = client?.id,
            flow = flow,
            handBack = redirectUri?.let { InteractiveFlowHandBack(it, InteractiveFlowRedirectType.AUTHORIZATION_CODE) },
            error = error,
        )

        // When no error, auto-consent consentable scopes at creation time.
        val consentableScopes = if (error == null) {
            (scopes ?: emptyList()).filterIsInstance<ConsentableUserScope>()
        } else null

        val entity = InteractiveFlowSessionOAuth2Entity(
            sessionId = session.id,
            clientId = client?.id,
            redirectUri = redirectUri?.toString(),
            requestedScopes = (scopes ?: emptyList()).map(Scope::scope).toTypedArray(),
            requestedIdTokenClaims = requestedClaims.idTokenClaimIds.toTypedArray(),
            requestedUserinfoClaims = requestedClaims.userInfoClaimIds.toTypedArray(),
            state = clientState,
            nonce = clientNonce,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod?.value,

            invitationId = invitationId,
            requestError = requestError?.errorCode,

            consentedScopes = consentableScopes?.map(Scope::scope)?.toTypedArray(),
            consentedAt = consentableScopes?.let { now },
            consentedBy = consentableScopes?.let { ConsentedBy.AUTO.name },
        )
        oauth2Repository.save(entity)

        return session
    }

    /**
     * Return the [InteractiveFlowSessionOAuth2] record attached to the [session], or throw an unrecoverable
     * [BusinessException] if the session has no attached OAuth2 record.
     *
     * Throws an internal [BusinessException] if the [session] is not an OAuth2 session (see [fetchOAuth2OrNull]).
     */
    suspend fun fetchOAuth2(session: InteractiveFlowSession): InteractiveFlowSessionOAuth2 {
        return fetchOAuth2OrNull(session) ?: throw businessExceptionOf(
            detailsId = "auth.interactive_flow_session.oauth2.missing"
        )
    }

    /**
     * Return the [InteractiveFlowSessionOAuth2] record attached to the [session], or null if the session
     * has no attached OAuth2 record.
     *
     * Throws an internal [BusinessException] if the [session]'s purpose is not [InteractiveFlowPurpose.OAUTH2_AUTHORIZE]
     * — asking for the OAuth2 request record of a non-OAuth2 session is a programming error.
     */
    suspend fun fetchOAuth2OrNull(session: InteractiveFlowSession): InteractiveFlowSessionOAuth2? {
        if (session.initiatingPurpose != InteractiveFlowPurpose.OAUTH2_AUTHORIZE) {
            throw internalBusinessExceptionOf(
                "auth.interactive_flow_session.oauth2.wrong_type",
                "type" to session.initiatingPurpose.name
            )
        }
        return oauth2Repository.findBySessionId(session.id)
            ?.let(oauth2Mapper::toInteractiveFlowSessionOAuth2)
    }

    /**
     * Return the client that started the [oauth2] request, which the flow's door of a claim's ACL may ask
     * about.
     *
     * Throws an unrecoverable [BusinessException] carrying ```client.invalid_client_id``` where the client the
     * authorization named is no longer configured.
     */
    suspend fun getClient(oauth2: InteractiveFlowSessionOAuth2): Client {
        return clientManager.findClientById(oauth2.clientId)
    }

    /**
     * Return the audience the [oauth2] request is for — the audience of the client that started it.
     *
     * Consent is recorded per audience, so this is the audience [InteractiveFlowSessionOAuth2.consentedScopes]
     * were consented for, and the one every consent-scoped read of that session's claims is made for.
     *
     * Throws an unrecoverable [BusinessException] carrying ```client.invalid_client_id``` where the client the
     * authorization named is no longer configured.
     */
    suspend fun getAudience(oauth2: InteractiveFlowSessionOAuth2): Audience {
        return getClient(oauth2).audience
    }

    /**
     * Record on this [session]'s OAuth2 record that the end-user has just proven a credential of the
     * account the authorization is for — a password checked, a third-party provider's callback resolved
     * to that account, the account created at sign-up, or a code proving a password reset — and that the
     * credential was the password identified by [passwordId], or none where [passwordId] is null.
     *
     * The date it writes is what every token issued out of this authorization states as `auth_time`, so
     * **call it only where a credential has verified *and* resolved this session's user**, which is the
     * condition
     * [UserSecurityContextManager.markProven][com.sympauthy.business.manager.securitycontext.UserSecurityContextManager.markProven]
     * is called under and for the same reason: a credential answers for whatever account matches it, and
     * a session whose user is fixed as somebody else must not be stamped by one proving a different
     * account.
     *
     * Writing it twice overwrites both, which is what a flow proving a credential a second time is owed:
     * the later proof is the one a token answers for, and the grant is tied to the password it proved.
     *
     * Throws an internal [BusinessException] where the [session] serves no authorization, as every
     * reader of this record does — a purpose issuing no token has no authentication time to state — and
     * where the write moved no row. The second is why it does not answer best-effort the way the place a
     * credential was proven at does: a lost stamp here ships an authorization whose every token states
     * no `auth_time`, which a resource server reads as a token issued before the claim existed.
     */
    suspend fun setAuthenticationDate(session: OnGoingInteractiveFlowSession, passwordId: UUID?) {
        checkAuthorization(session)
        val updated = oauth2Repository.updateAuthenticationDate(
            sessionId = session.id,
            authenticationDate = LocalDateTime.now(),
            passwordId = passwordId
        )
        checkUpdated(session, updated)
    }

    /**
     * Record that the credential proven at the authorization's authentication date was the password identified by
     * [passwordId], for a credential that only became a password once it was proven: a reset's new password is
     * written as the session completes, after the code that proved the account set the date.
     *
     * Throws as [setAuthenticationDate] does, and for the same reasons.
     */
    suspend fun setPasswordId(session: OnGoingInteractiveFlowSession, passwordId: UUID) {
        checkAuthorization(session)
        checkUpdated(session, oauth2Repository.updatePasswordId(sessionId = session.id, passwordId = passwordId))
    }

    private fun checkAuthorization(session: OnGoingInteractiveFlowSession) {
        if (session.initiatingPurpose != InteractiveFlowPurpose.OAUTH2_AUTHORIZE) {
            throw internalBusinessExceptionOf(
                "auth.interactive_flow_session.oauth2.wrong_type",
                "type" to session.initiatingPurpose.name
            )
        }
    }

    private fun checkUpdated(session: OnGoingInteractiveFlowSession, updated: Int) {
        if (updated == 0) {
            throw internalBusinessExceptionOf(
                "auth.interactive_flow_session.oauth2.missing",
                "sessionId" to session.id.toString()
            )
        }
    }

    /**
     * Set and save the consentable scopes obtained through user consent for this session's OAuth2 record.
     */
    suspend fun setConsentedScopes(
        session: OnGoingInteractiveFlowSession,
        consentedScopes: List<EnabledScope>,
        consentedBy: ConsentedBy
    ): InteractiveFlowSessionOAuth2 {
        val consentedScopeIds = consentedScopes.map(Scope::scope)
        val consentedAt = LocalDateTime.now()
        oauth2Repository.updateConsentedScopes(
            sessionId = session.id,
            consentedScopes = consentedScopeIds,
            consentedAt = consentedAt,
            consentedBy = consentedBy.name
        )
        return fetchOAuth2(session)
    }

    /**
     * Set and save the grantable scopes granted through granting rules for this session's OAuth2 record.
     */
    suspend fun setGrantedScopes(
        session: OnGoingInteractiveFlowSession,
        grantedScopes: List<EnabledScope>,
        grantedBy: GrantedBy
    ): InteractiveFlowSessionOAuth2 {
        val grantedScopeIds = grantedScopes.map(Scope::scope)
        val grantedAt = LocalDateTime.now()
        oauth2Repository.updateGrantedScopes(
            sessionId = session.id,
            grantedScopes = grantedScopeIds,
            grantedAt = grantedAt,
            grantedBy = grantedBy.name
        )
        return fetchOAuth2(session)
    }
}
