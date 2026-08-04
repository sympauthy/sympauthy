package com.sympauthy.business.manager.flow.link

import com.sympauthy.business.exception.internalBusinessExceptionOf
import com.sympauthy.business.manager.flow.InteractiveFlowSessionManager
import com.sympauthy.business.manager.flow.confirm.InteractiveFlowSessionConfirmManager
import com.sympauthy.business.mapper.InteractiveFlowSessionLinkProviderMapper
import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.ConfirmActionType
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowRedirectType
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowSessionLinkProvider
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.data.model.InteractiveFlowSessionLinkProviderEntity
import com.sympauthy.data.repository.InteractiveFlowSessionLinkProviderRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.URI
import java.util.*

/**
 * Manager owning the [InteractiveFlowPurpose.LINK_PROVIDER] purpose end to end. It provides methods to:
 * - fetch / store the link intent (the target provider) attached to a session.
 * - start a standalone [InteractiveFlowPurpose.LINK_PROVIDER] session for an already-identified user
 *   (client- or admin-initiated provider linking).
 *
 * The fetch / store methods are deliberately thin (repository + mapper only) so the purpose handler can inject
 * this manager without introducing a dependency cycle through the flow engine.
 */
@Singleton
open class InteractiveFlowSessionLinkProviderManager(
    @Inject private val sessionManager: InteractiveFlowSessionManager,
    @Inject private val confirmManager: InteractiveFlowSessionConfirmManager,
    @Inject private val linkProviderRepository: InteractiveFlowSessionLinkProviderRepository,
    @Inject private val linkProviderMapper: InteractiveFlowSessionLinkProviderMapper,
) {

    /**
     * Return the [InteractiveFlowSessionLinkProvider] intent attached to the [session], or null if the session
     * carries no link-provider purpose.
     */
    suspend fun fetchLinkProviderOrNull(session: InteractiveFlowSession): InteractiveFlowSessionLinkProvider? {
        return linkProviderRepository.findBySessionId(session.id)
            ?.let(linkProviderMapper::toInteractiveFlowSessionLinkProvider)
    }

    /**
     * Store (upsert) the target [providerId] to link on the [session]'s link-provider record.
     */
    suspend fun setLinkProvider(
        session: OnGoingInteractiveFlowSession,
        providerId: String
    ): InteractiveFlowSessionLinkProvider {
        val entity = InteractiveFlowSessionLinkProviderEntity(
            sessionId = session.id,
            providerId = providerId
        )
        if (linkProviderRepository.findBySessionId(session.id) != null) {
            linkProviderRepository.update(entity)
        } else {
            linkProviderRepository.save(entity)
        }
        return linkProviderMapper.toInteractiveFlowSessionLinkProvider(entity)
    }

    /**
     * Start a [InteractiveFlowPurpose.LINK_PROVIDER] session for the already-identified [userId] to link the
     * provider [providerId] to their account, returning the end-user to [returnUri] on success (or [cancelUri]
     * on cancellation, as a [InteractiveFlowRedirectType.PLAIN] redirect), in a single transaction.
     *
     * The action is initiated on the end-user's behalf — by a client, or by an administrator when
     * [initiatingClientId] is null — so the session is gated by a prepended [InteractiveFlowPurpose.CONFIRM]
     * step whose parameter (including [initiatingClientId]) is stored on the confirm record. The session runs
     * the ordered purposes `[CONFIRM, REAUTHENTICATION, LINK_PROVIDER]`.
     *
     * The [InteractiveFlowPurpose.REAUTHENTICATION] gate is a deliberate, security-load-bearing choice —
     * **do not remove it**:
     *
     * Linking a provider mints a **durable login credential** on the account (unlike MFA enrollment, which only
     * adds a factor). The caller merely *names* the end-user — a client through their access token, or an
     * administrator through the user id — which authorizes the caller to act for that user but proves nothing
     * about who is driving the browser that completes the link. A client-presented token cannot stand in for
     * that proof: it is presented server-side, its `iat` is token-issuance (not an `auth_time`) and is
     * refresh-forgeable, and there is no end-user browser session to measure freshness against. Without an
     * interactive re-authentication bound to the fixed [OnGoingInteractiveFlowSession.userId], anyone able to
     * drive the browser (a stolen token, a shared device, a compromised client) could attach a provider they
     * control and take over the account. Re-authentication is the only proof that the browser user genuinely
     * owns the account; it *confirms* the pre-set user and must never *establish* a different one.
     *
     * See #294 (this flow) and #295 (the REAUTHENTICATION purpose) for the full rationale.
     *
     * The [returnUri] and [cancelUri] must have been validated (e.g. against the named client's registered
     * redirect URIs) by the caller before reaching here.
     */
    @Transactional
    open suspend fun startLinkProviderSession(
        userId: UUID,
        providerId: String,
        returnUri: URI,
        flow: AuthorizationFlow,
        initiatingClientId: String?,
        cancelUri: URI? = null,
    ): OnGoingInteractiveFlowSession {
        val session = sessionManager.newSession(
            purposes = listOf(
                InteractiveFlowPurpose.CONFIRM,
                InteractiveFlowPurpose.REAUTHENTICATION,
                InteractiveFlowPurpose.LINK_PROVIDER,
            ),
            initiatingPurpose = InteractiveFlowPurpose.LINK_PROVIDER,
            flow = flow,
            successRedirectUri = returnUri,
            redirectType = InteractiveFlowRedirectType.PLAIN,
            cancelRedirectUri = cancelUri,
        ) as? OnGoingInteractiveFlowSession
            ?: throw internalBusinessExceptionOf("flow.link_provider.start.not_ongoing")
        val sessionWithUser = sessionManager.setAuthenticatedUserId(session, userId)
        confirmManager.setConfirm(
            session = sessionWithUser,
            action = ConfirmActionType.LINK_PROVIDER,
            clientId = initiatingClientId
        )
        setLinkProvider(sessionWithUser, providerId)
        return sessionWithUser
    }
}
