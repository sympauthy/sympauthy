package com.sympauthy.business.model.flow

import com.sympauthy.business.model.Expirable
import java.net.URI
import java.time.LocalDateTime
import java.util.*

/**
 * Hold the flow-generic state of an end-user's journey through an interactive flow.
 *
 * This is the primary primitive the whole interactive flow passes around. It carries only state that is
 * common to every flow purpose (progress, expiration, terminal status). Concern-specific state lives in
 * its own record attached by [id] and fetched via the matching manager, never carried here:
 * - the client's OAuth2 request context + consent/grant in [InteractiveFlowSessionOAuth2],
 * - the state of authorizing the user through a third-party provider in [InteractiveFlowSessionProvider].
 *
 * Designer note: By defining a sealed class, we ensure that business logic can only interact on ongoing
 * sessions without having to check the status in every business method. It forces the developer to check
 * the status of the session before interacting with it.
 */
sealed class InteractiveFlowSession(
    /**
     * An uniq identifier for the session.
     */
    val id: UUID,

    /**
     * The ordered list of purposes this session serves.
     *
     * The engine drives them in order: the first purpose is the one that initiated the session and owns the
     * terminal handoff (e.g. issuing an authorization code); further purposes are appended by a purpose as it
     * resolves (e.g. the OAuth2 authorize purpose appending an MFA purpose) and must all resolve before the
     * session completes.
     */
    val purposes: List<InteractiveFlowPurpose>,

    /**
     * The identifier of the interactive flow the user is going through.
     * null for non-interactive flows.
     */
    val flowId: String?,

    override val expirationDate: LocalDateTime
) : Expirable {

    /**
     * The purpose that initiated the session and owns its terminal handoff.
     *
     * [InteractiveFlowPurpose.CONFIRM] is a gate prepended in front of the initiating purpose, so it is
     * skipped here: the initiating purpose is the first non-[InteractiveFlowPurpose.CONFIRM] purpose.
     */
    val initiatingPurpose: InteractiveFlowPurpose
        get() = purposes.first { it != InteractiveFlowPurpose.CONFIRM }
}

/**
 * Represents an interactive flow session that is ongoing.
 */
class OnGoingInteractiveFlowSession(
    id: UUID,
    purposes: List<InteractiveFlowPurpose>,
    flowId: String?,
    expirationDate: LocalDateTime,

    /**
     * When the user initiated the session.
     */
    val sessionDate: LocalDateTime,

    /**
     * The identifier of the user that has been authenticated during this session.
     * Null until the user has been identified.
     */
    val userId: UUID?,

    /**
     * True if the user was created (signed up) during this session, false if an existing user signed in.
     * Meaningful only once the user has been identified.
     */
    val signedUp: Boolean = false,

    /**
     * The purposes of this session that have already been completed.
     *
     * A session's purposes are completed one at a time (each purpose's handler completes its own purpose as
     * the engine hands off); the session as a whole completes only once this covers every entry of [purposes].
     */
    val completedPurposes: List<InteractiveFlowPurpose> = emptyList(),

    /**
     * When the end-user successfully completed the MFA step for this session.
     * Null if MFA has not been completed yet.
     */
    val mfaPassedDate: LocalDateTime? = null,

    /**
     * The URI the end-user is handed back to once the session completes. How it is decorated is driven by
     * [redirectType] (an authorization code is appended for [InteractiveFlowRedirectType.AUTHORIZATION_CODE],
     * nothing for [InteractiveFlowRedirectType.PLAIN]).
     *
     * Null while the session is still being set up, or when the initiating request failed validation and has
     * no usable redirect target.
     */
    val successRedirectUri: URI? = null,

    /**
     * How the end-user must be handed back to the initiator on a terminal status. Null until the session's
     * redirect target has been set.
     */
    val redirectType: InteractiveFlowRedirectType? = null,

    /**
     * The URI the end-user is handed back to when they cancel the session. For an
     * [InteractiveFlowRedirectType.AUTHORIZATION_CODE] session this is the client redirect URI (the OAuth2
     * `error=access_denied` response is returned there); for a [InteractiveFlowRedirectType.PLAIN] session
     * this is a caller-provided cancel URI, and may be null when the flow does not offer cancellation.
     */
    val cancelRedirectUri: URI? = null,
) : InteractiveFlowSession(
    id = id,
    purposes = purposes,
    flowId = flowId,
    expirationDate = expirationDate
) {
    /**
     * True if the end-user has successfully completed the MFA step for this session.
     */
    val mfaPassed: Boolean get() = mfaPassedDate != null

    fun copy(
        purposes: List<InteractiveFlowPurpose>? = null,
        completedPurposes: List<InteractiveFlowPurpose>? = null,
        userId: UUID? = null,
        signedUp: Boolean? = null,
        mfaPassedDate: LocalDateTime? = null,
    ) = OnGoingInteractiveFlowSession(
        id = this.id,
        purposes = purposes ?: this.purposes,
        flowId = this.flowId,
        expirationDate = this.expirationDate,
        sessionDate = this.sessionDate,
        userId = userId ?: this.userId,
        signedUp = signedUp ?: this.signedUp,
        completedPurposes = completedPurposes ?: this.completedPurposes,
        mfaPassedDate = mfaPassedDate ?: this.mfaPassedDate,
        successRedirectUri = this.successRedirectUri,
        redirectType = this.redirectType,
        cancelRedirectUri = this.cancelRedirectUri,
    )
}

/**
 * Represents an interactive flow session that has completed.
 *
 * Once the [InteractiveFlowSession] is completed, it can be considered as a successful authentication and
 * this authorization server will emit an authentication token.
 */
class CompletedInteractiveFlowSession(
    id: UUID,
    purposes: List<InteractiveFlowPurpose>,
    flowId: String?,
    expirationDate: LocalDateTime,

    /**
     * When the user initiated the session.
     */
    val sessionDate: LocalDateTime,

    /**
     * The identifier of the user that has been authenticated during this session.
     */
    val userId: UUID,

    /**
     * True if the user was created (signed up) during this session, false if an existing user signed in.
     */
    val signedUp: Boolean = false,

    /**
     * The purposes of this session that have been completed. For a completed session this covers every entry
     * of [purposes].
     */
    val completedPurposes: List<InteractiveFlowPurpose> = emptyList(),

    /**
     * When the end-user successfully completed the MFA step for this session.
     * Null if MFA has not been completed.
     */
    val mfaPassedDate: LocalDateTime? = null,

    /**
     * When the user has completed the interactive flow.
     */
    val completeDate: LocalDateTime,

    /**
     * The URI the end-user must be handed back to now the session has completed. Decorated according to
     * [redirectType].
     */
    val successRedirectUri: URI,

    /**
     * How the end-user must be handed back to the initiator: an authorization code is appended for
     * [InteractiveFlowRedirectType.AUTHORIZATION_CODE], nothing for [InteractiveFlowRedirectType.PLAIN].
     */
    val redirectType: InteractiveFlowRedirectType,

    /**
     * The URI the end-user would have been handed back to had they cancelled the session. Carried for
     * symmetry; may be null when the flow does not offer cancellation.
     */
    val cancelRedirectUri: URI? = null,
) : InteractiveFlowSession(
    id = id,
    purposes = purposes,
    flowId = flowId,
    expirationDate = expirationDate
)

/**
 * Represents an interactive flow session that failed.
 */
class FailedInteractiveFlowSession(
    id: UUID,
    purposes: List<InteractiveFlowPurpose>,
    flowId: String?,
    expirationDate: LocalDateTime,

    /**
     * Identifier of the message detailing, in a technical way, the error which caused this session to fail.
     * This value is copied from the non-recoverable business exception thrown during the execution of the flow.
     */
    val errorDetailsId: String,

    /**
     * Identifier of the message detailing, for the end-user, the error which caused this session to fail.
     * This value is copied from the non-recoverable business exception thrown during the execution of the flow.
     */
    val errorDescriptionId: String? = null,

    /**
     * Value to expose to the mustache template to inject values into the localized error messages.
     */
    val errorValues: Map<String, String>? = null,

    /**
     * When the interactive flow failed.
     */
    val errorDate: LocalDateTime,
) : InteractiveFlowSession(
    id = id,
    purposes = purposes,
    flowId = flowId,
    expirationDate = expirationDate
)

/**
 * Represents an interactive flow session the end-user cancelled.
 *
 * Distinct from a [FailedInteractiveFlowSession]: a failed session routes the end-user to the flow's error
 * page, whereas a cancelled session hands the end-user back to the flow's initiator on the cancellation
 * redirect (the OAuth2 `error=access_denied` response, or a caller-provided cancel URI).
 */
class CancelledInteractiveFlowSession(
    id: UUID,
    purposes: List<InteractiveFlowPurpose>,
    flowId: String?,
    expirationDate: LocalDateTime,

    /**
     * The identifier of the user that had been authenticated when the session was cancelled.
     * Null when the user cancelled before identifying themselves (e.g. at the sign-in step).
     */
    val userId: UUID?,

    /**
     * How the end-user must be handed back to the initiator on cancellation.
     */
    val redirectType: InteractiveFlowRedirectType,

    /**
     * The URI the end-user was handed back to on completion had the session succeeded. Carried for symmetry;
     * null when the initiating request had no usable redirect target.
     */
    val successRedirectUri: URI?,

    /**
     * The URI the end-user is handed back to on cancellation. For [InteractiveFlowRedirectType.PLAIN] this is
     * the caller-provided cancel URI; for [InteractiveFlowRedirectType.AUTHORIZATION_CODE] this is the client
     * redirect URI where the OAuth2 `error=access_denied` response is returned.
     */
    val cancelRedirectUri: URI?,

    /**
     * When the end-user cancelled the interactive flow.
     */
    val cancelDate: LocalDateTime,
) : InteractiveFlowSession(
    id = id,
    purposes = purposes,
    flowId = flowId,
    expirationDate = expirationDate
)
