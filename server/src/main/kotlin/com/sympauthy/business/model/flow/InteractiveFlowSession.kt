package com.sympauthy.business.model.flow

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.Expirable
import com.sympauthy.monitoring.telemetry.manager.SpanReference
import java.time.LocalDateTime
import java.util.*

/**
 * Hold the flow-generic state of an end-user's journey through an interactive flow. Concern-specific state
 * lives in its own record attached by [id] and fetched via the matching manager, never here — such as
 * [InteractiveFlowSessionOAuth2] and [InteractiveFlowSessionProvider]. See `docs/design/interactive-flow.md`.
 */
sealed class InteractiveFlowSession(
    /**
     * An uniq identifier for the session.
     */
    val id: UUID,

    /**
     * The ordered list of purposes this session serves, which the engine drives in order and which grows
     * while the session runs; every one must resolve before the session completes. Its first entry is not
     * the initiating one where a gate stands in front — [initiatingPurpose] is. See
     * `docs/design/interactive-flow.md`.
     */
    val purposes: List<InteractiveFlowPurpose>,

    /**
     * The purpose that initiated the session and owns its terminal handoff.
     *
     * Set once when the session is created — the starter names the purpose it creates the session for — and
     * stored rather than derived from [purposes]; see `docs/design/interactive-flow.md`.
     */
    val initiatingPurpose: InteractiveFlowPurpose,

    /**
     * The identifier of the client that initiated the session, or null where an administrator initiated it
     * and where the initiating purpose has no client at all.
     *
     * Set once when the session is created, in the same transaction as the attached record it duplicates
     * ([InteractiveFlowSessionOAuth2] for an authorization, [InteractiveFlowSessionConfirm] for a gated one),
     * and never updated; see `docs/design/interactive-flow.md`.
     *
     * It names a client this deployment configured at the moment the session started. Configuration is not a
     * table, so a session may outlive the client it names.
     */
    val initiatingClientId: String?,

    /**
     * The identifier of the interactive flow the user is going through.
     * null for non-interactive flows.
     */
    val flowId: String?,

    override val expirationDate: LocalDateTime,

    /**
     * The span of the request that started the session, which every later request acting on it links to. Null
     * where that request was not traced, and set once when the session is created.
     */
    val startedBy: SpanReference?
) : Expirable {
    /**
     * Where and how the end-user is handed back to whoever started the session, whatever way it ends. Set once
     * when the session is created, and null where the start request named nowhere this server may send the
     * end-user back to, so such a session is left only through its error page. A completed and a cancelled
     * session always carry one. See `docs/design/interactive-flow.md`.
     */
    abstract val handBack: InteractiveFlowHandBack?
}

/**
 * Represents an interactive flow session that is ongoing.
 */
class OnGoingInteractiveFlowSession(
    id: UUID,
    purposes: List<InteractiveFlowPurpose>,
    initiatingPurpose: InteractiveFlowPurpose,
    initiatingClientId: String?,
    flowId: String?,
    expirationDate: LocalDateTime,
    startedBy: SpanReference?,
    override val handBack: InteractiveFlowHandBack?,

    /**
     * When the user initiated the session.
     */
    val sessionDate: LocalDateTime,

    /**
     * Optimistic-concurrency version of the underlying row, as it was last read. Every guarded
     * lifecycle mutation is a compare-and-swap against this value and returns a session carrying the
     * incremented version; see `docs/design/interactive-flow.md`.
     *
     * Defaults to 0 (a freshly-created row) purely for construction ergonomics; the mapper always
     * supplies the persisted value read from the database.
     */
    val version: Long = 0,

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

) : InteractiveFlowSession(
    id = id,
    purposes = purposes,
    initiatingPurpose = initiatingPurpose,
    initiatingClientId = initiatingClientId,
    flowId = flowId,
    expirationDate = expirationDate,
    startedBy = startedBy
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
        version: Long? = null,
    ) = OnGoingInteractiveFlowSession(
        id = this.id,
        purposes = purposes ?: this.purposes,
        initiatingPurpose = this.initiatingPurpose,
        initiatingClientId = this.initiatingClientId,
        flowId = this.flowId,
        expirationDate = this.expirationDate,
        startedBy = this.startedBy,
        handBack = this.handBack,
        sessionDate = this.sessionDate,
        version = version ?: this.version,
        userId = userId ?: this.userId,
        signedUp = signedUp ?: this.signedUp,
        completedPurposes = completedPurposes ?: this.completedPurposes,
        mfaPassedDate = mfaPassedDate ?: this.mfaPassedDate,
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
    initiatingPurpose: InteractiveFlowPurpose,
    initiatingClientId: String?,
    flowId: String?,
    expirationDate: LocalDateTime,
    startedBy: SpanReference?,
    override val handBack: InteractiveFlowHandBack,

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
) : InteractiveFlowSession(
    id = id,
    purposes = purposes,
    initiatingPurpose = initiatingPurpose,
    initiatingClientId = initiatingClientId,
    flowId = flowId,
    expirationDate = expirationDate,
    startedBy = startedBy
)

/**
 * Represents an interactive flow session that failed.
 */
class FailedInteractiveFlowSession(
    id: UUID,
    purposes: List<InteractiveFlowPurpose>,
    initiatingPurpose: InteractiveFlowPurpose,
    initiatingClientId: String?,
    flowId: String?,
    expirationDate: LocalDateTime,
    startedBy: SpanReference?,
    override val handBack: InteractiveFlowHandBack?,

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
     * Values interpolated into the `{name}` placeholders of the localized error messages, copied from the
     * same exception.
     */
    val errorValues: Map<String, String>? = null,

    /**
     * When the interactive flow failed.
     */
    val errorDate: LocalDateTime,

    /**
     * What the session had become when its expiration passed, where this failure is that expiry rather than one
     * recorded on the session: [InteractiveFlowSessionStatus.COMPLETED] or [InteractiveFlowSessionStatus.CANCELLED]
     * where it had already ended, [InteractiveFlowSessionStatus.EXPIRED] where it was still running. Null for a
     * recorded failure.
     */
    val statusAtExpiry: InteractiveFlowSessionStatus? = null,
) : InteractiveFlowSession(
    id = id,
    purposes = purposes,
    initiatingPurpose = initiatingPurpose,
    initiatingClientId = initiatingClientId,
    flowId = flowId,
    expirationDate = expirationDate,
    startedBy = startedBy
)

/**
 * The failure a session that ran out of time ended with, named once for every reader of it.
 *
 * **Nothing throws this.** An expiry is not a rule refusing anything — no request failed, the person
 * simply stopped — so no column records it and there is nothing on the row to read back. It is
 * synthesised where an expired session has to say what became of it: to route the person to the error
 * page, and to answer an operator opening that session afterwards. Both read it from here, so the two
 * cannot end up saying different things about one session.
 *
 * [expirationDate] is interpolated into the technical message and is the moment the session stopped
 * being usable, which is also the moment it ended.
 */
fun interactiveFlowSessionExpiredExceptionOf(expirationDate: LocalDateTime) = businessExceptionOf(
    "auth.interactive_flow_session.validate.expired",
    "description.oauth2.expired",
    "expirationDate" to expirationDate.toString()
)

/**
 * What the end-user is told of a session answered [InteractiveFlowStep.AlreadyCompleted]: it completed, and the
 * hand-off its initiator gives once went to the request that completed it.
 *
 * **Nothing throws this either.** The session did not fail, and nothing on its row says what the person
 * reaching it again should read, so it is synthesised where that answer is rendered.
 */
fun interactiveFlowSessionAlreadyCompletedExceptionOf() = businessExceptionOf(
    "auth.interactive_flow_session.already_completed",
    "description.auth.interactive_flow_session.already_completed"
)

/**
 * The code a session the end-user left while it was still running is published under, beside the codes of the
 * failures a session ends on, to whoever the session hands the end-user back to.
 *
 * **Nothing throws it.** Leaving is not a failure, and no message renders it: a cancelled session never
 * reaches the error page.
 */
const val CANCELLED_DETAILS_ID = "auth.interactive_flow_session.cancelled"

/**
 * Detail id of the failure [interactiveFlowSessionConcurrentModificationExceptionOf] builds, for the flow's
 * error handling to tell it apart (see
 * [com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil.handleException]).
 */
const val CONCURRENT_MODIFICATION_DETAILS_ID = "auth.interactive_flow_session.concurrent_modification"

/**
 * The non-recoverable failure of a version-guarded update that affected no rows: the [session] snapshot the
 * caller holds is stale because another request advanced the session since it was read. The flow's error
 * handling reflects the session's current terminal status where possible and otherwise fails it.
 *
 * A write that another request beat to a row of its own, rather than to the session's version, throws it
 * too where losing that race means the same thing.
 */
fun interactiveFlowSessionConcurrentModificationExceptionOf(session: InteractiveFlowSession) =
    businessExceptionOf(
        CONCURRENT_MODIFICATION_DETAILS_ID,
        "sessionId" to session.id.toString()
    )

/**
 * Represents an interactive flow session the end-user cancelled.
 *
 * Distinct from a [FailedInteractiveFlowSession]: a failed session routes the end-user to the flow's error
 * page, whereas a cancelled session hands the end-user back to the flow's initiator (the OAuth2
 * `error=access_denied` response, or the return URI saying the flow was cancelled).
 */
class CancelledInteractiveFlowSession(
    id: UUID,
    purposes: List<InteractiveFlowPurpose>,
    initiatingPurpose: InteractiveFlowPurpose,
    initiatingClientId: String?,
    flowId: String?,
    expirationDate: LocalDateTime,
    startedBy: SpanReference?,
    override val handBack: InteractiveFlowHandBack,

    /**
     * The identifier of the user that had been authenticated when the session was cancelled.
     * Null when the user cancelled before identifying themselves (e.g. at the sign-in step).
     */
    val userId: UUID?,

    /**
     * When the end-user cancelled the interactive flow.
     */
    val cancelDate: LocalDateTime,
) : InteractiveFlowSession(
    id = id,
    purposes = purposes,
    initiatingPurpose = initiatingPurpose,
    initiatingClientId = initiatingClientId,
    flowId = flowId,
    expirationDate = expirationDate,
    startedBy = startedBy
)

/**
 * The user this session identified, or null where it never identified one — and where the session failed,
 * since a failed session is read without its user by design.
 *
 * The sealed base does not carry the id because three of the four subtypes disagree about it: a completed
 * session always has one, an ongoing and a cancelled one may, and a failed one is not read for it. This is
 * for a reader that has to answer for any of them, and it is deliberately not named `userId`: a caller
 * holding the subtype should reach that subtype's own property and get the nullability it actually has.
 */
val InteractiveFlowSession.userIdOrNull: UUID?
    get() = when (this) {
        is OnGoingInteractiveFlowSession -> userId
        is CompletedInteractiveFlowSession -> userId
        is CancelledInteractiveFlowSession -> userId
        is FailedInteractiveFlowSession -> null
    }

/**
 * What became of this session, as [InteractiveFlowSessionStatus] reads it: an expiry ends only a session that was
 * still running, so one that had completed or been cancelled before its expiration passed answers that ending.
 */
val InteractiveFlowSession.status: InteractiveFlowSessionStatus
    get() = when (this) {
        is OnGoingInteractiveFlowSession -> InteractiveFlowSessionStatus.ONGOING
        is CompletedInteractiveFlowSession -> InteractiveFlowSessionStatus.COMPLETED
        is CancelledInteractiveFlowSession -> InteractiveFlowSessionStatus.CANCELLED
        is FailedInteractiveFlowSession -> statusAtExpiry ?: InteractiveFlowSessionStatus.FAILED
    }

/**
 * When the end-user passed the MFA step of this session, or null where they did not — and where the session
 * is one that does not carry the date.
 *
 * A cancelled and a failed session drop it, so this answers null for both rather than saying MFA was never
 * passed. See [userIdOrNull] for why the name is not the subtypes' own.
 */
val InteractiveFlowSession.mfaPassedDateOrNull: LocalDateTime?
    get() = when (this) {
        is OnGoingInteractiveFlowSession -> mfaPassedDate
        is CompletedInteractiveFlowSession -> mfaPassedDate
        is CancelledInteractiveFlowSession, is FailedInteractiveFlowSession -> null
    }
