package com.sympauthy.business.model.flow

import com.sympauthy.business.exception.businessExceptionOf
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
     * The purpose that initiated the session and owns its terminal handoff.
     *
     * Set once when the session is created — the starter names the purpose it creates the session for — and
     * carried for the whole life of the session. It is deliberately **stored** rather than derived from
     * [purposes]: gate purposes ([InteractiveFlowPurpose.CONFIRM], [InteractiveFlowPurpose.REAUTHENTICATION])
     * are prepended and second-factor purposes ([InteractiveFlowPurpose.MFA_CHALLENGE]) are inserted at
     * runtime, so no positional rule over [purposes] stays correct as the chain grows.
     */
    val initiatingPurpose: InteractiveFlowPurpose,

    /**
     * The identifier of the client that initiated the session, or null where an administrator initiated it
     * and where the initiating purpose has no client at all.
     *
     * Set once when the session is created and never updated, for the reason [initiatingPurpose] is: the
     * client is on the attached record of whichever purpose initiated the session
     * ([InteractiveFlowSessionOAuth2] for an authorization, [InteractiveFlowSessionConfirm] for a gated one),
     * so no rule over [purposes] reaching for it stays correct as purposes gain initiators. It is written in
     * the same transaction as the record it duplicates, which is what keeps the two from disagreeing.
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

    override val expirationDate: LocalDateTime
) : Expirable

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

    /**
     * When the user initiated the session.
     */
    val sessionDate: LocalDateTime,

    /**
     * Optimistic-concurrency version of the underlying row, as it was last read. Every guarded
     * lifecycle mutation is a compare-and-swap against this value (and returns a session carrying the
     * incremented version); a stale value loses the swap, so a replayed / concurrent request cannot
     * silently overwrite newer state.
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
    initiatingPurpose = initiatingPurpose,
    initiatingClientId = initiatingClientId,
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
        version: Long? = null,
    ) = OnGoingInteractiveFlowSession(
        id = this.id,
        purposes = purposes ?: this.purposes,
        initiatingPurpose = this.initiatingPurpose,
        initiatingClientId = this.initiatingClientId,
        flowId = this.flowId,
        expirationDate = this.expirationDate,
        sessionDate = this.sessionDate,
        version = version ?: this.version,
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
    initiatingPurpose: InteractiveFlowPurpose,
    initiatingClientId: String?,
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
    initiatingPurpose = initiatingPurpose,
    initiatingClientId = initiatingClientId,
    flowId = flowId,
    expirationDate = expirationDate
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
    initiatingPurpose = initiatingPurpose,
    initiatingClientId = initiatingClientId,
    flowId = flowId,
    expirationDate = expirationDate
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
 * Represents an interactive flow session the end-user cancelled.
 *
 * Distinct from a [FailedInteractiveFlowSession]: a failed session routes the end-user to the flow's error
 * page, whereas a cancelled session hands the end-user back to the flow's initiator on the cancellation
 * redirect (the OAuth2 `error=access_denied` response, or a caller-provided cancel URI).
 */
class CancelledInteractiveFlowSession(
    id: UUID,
    purposes: List<InteractiveFlowPurpose>,
    initiatingPurpose: InteractiveFlowPurpose,
    initiatingClientId: String?,
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
    initiatingPurpose = initiatingPurpose,
    initiatingClientId = initiatingClientId,
    flowId = flowId,
    expirationDate = expirationDate
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
