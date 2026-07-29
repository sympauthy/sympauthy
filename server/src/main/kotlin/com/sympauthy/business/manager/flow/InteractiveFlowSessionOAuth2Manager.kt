package com.sympauthy.business.manager.flow

import com.sympauthy.api.exception.oauth2ExceptionOf
import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.mapper.InteractiveFlowSessionOAuth2Mapper
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionOAuth2
import com.sympauthy.business.model.flow.InteractiveFlowSessionType
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.oauth2.OAuth2ErrorCode.INVALID_REQUEST
import com.sympauthy.data.model.InteractiveFlowSessionOAuth2Entity
import com.sympauthy.data.repository.InteractiveFlowSessionOAuth2Repository
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.net.URI
import java.time.LocalDateTime
import java.util.*

/**
 * Manager in charge of the OAuth2 request context attached to an [InteractiveFlowSession].
 * It provides methods to:
 * - start an OAuth2 session (the session + its attached OAuth2 record, atomically).
 * - fetch the OAuth2 record of a session.
 * - modify the OAuth2 record: consented scopes, granted scopes.
 * - detect an OAuth2 request replay by its state.
 *
 * It builds on top of [InteractiveFlowSessionManager] for the flow-generic session lifecycle.
 */
@Singleton
open class InteractiveFlowSessionOAuth2Manager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val oauth2Repository: InteractiveFlowSessionOAuth2Repository,
    @Inject private val oauth2Mapper: InteractiveFlowSessionOAuth2Mapper,
) {

    /**
     * Create a new OAuth2 [InteractiveFlowSession] for the end-user and save it in the database, together
     * with its attached [InteractiveFlowSessionOAuth2Entity] record, in a single transaction.
     *
     * The session is saved even if the validation of some parameters failed (i.e. [error] is non-null).
     */
    @Transactional
    open suspend fun startOAuth2Session(
        client: Client? = null,
        clientState: String? = null,
        clientNonce: String? = null,
        flow: AuthorizationFlow? = null,
        scopes: List<Scope>? = null,
        redirectUri: URI? = null,
        codeChallenge: String? = null,
        codeChallengeMethod: CodeChallengeMethod? = null,
        invitationId: UUID? = null,
        error: BusinessException? = null
    ): InteractiveFlowSession {
        val now = LocalDateTime.now()
        val session = sessionManager.newSession(InteractiveFlowSessionType.OAUTH2, flow, error)

        // When no error, auto-consent consentable scopes at creation time.
        val consentableScopes = if (error == null) {
            (scopes ?: emptyList()).filterIsInstance<ConsentableUserScope>()
        } else null

        val entity = InteractiveFlowSessionOAuth2Entity(
            sessionId = session.id,
            clientId = client?.id,
            redirectUri = redirectUri?.toString(),
            requestedScopes = (scopes ?: emptyList()).map(Scope::scope).toTypedArray(),
            state = clientState,
            nonce = clientNonce,
            codeChallenge = codeChallenge,
            codeChallengeMethod = codeChallengeMethod?.value,

            invitationId = invitationId,

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
     * Throws an internal [BusinessException] if the [session]'s type is not [InteractiveFlowSessionType.OAUTH2]
     * — asking for the OAuth2 request record of a non-OAuth2 session is a programming error.
     */
    suspend fun fetchOAuth2OrNull(session: InteractiveFlowSession): InteractiveFlowSessionOAuth2? {
        if (session.type != InteractiveFlowSessionType.OAUTH2) {
            throw internalBusinessExceptionOf(
                "auth.interactive_flow_session.oauth2.wrong_type",
                "type" to session.type.name
            )
        }
        return oauth2Repository.findBySessionId(session.id)
            ?.let(oauth2Mapper::toInteractiveFlowSessionOAuth2)
    }

    /**
     * Throw an [OAuth2Exception] if an OAuth2 session already exists with the given [uncheckedClientState],
     * which would indicate a replay of the authorize request.
     */
    suspend fun checkIsExistingOAuth2WithState(
        uncheckedClientState: String?
    ) {
        val existing = uncheckedClientState?.let {
            oauth2Repository.findByState(it)
        }
        if (existing != null) {
            throw oauth2ExceptionOf(INVALID_REQUEST, "authorize.existing_state", "description.oauth2.replay")
        }
    }

    /**
     * Set and save the consentable scopes obtained through user consent for this session's OAuth2 record.
     */
    suspend fun setConsentedScopes(
        session: OnGoingInteractiveFlowSession,
        consentedScopes: List<Scope>,
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
        grantedScopes: List<Scope>,
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
