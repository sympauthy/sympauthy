package com.sympauthy.business.model.flow

import com.sympauthy.business.model.Expirable
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
     */
    val initiatingPurpose: InteractiveFlowPurpose get() = purposes.first()
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
     * When the end-user successfully completed the MFA step for this session.
     * Null if MFA has not been completed yet.
     */
    val mfaPassedDate: LocalDateTime? = null,
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
        userId: UUID? = null,
        mfaPassedDate: LocalDateTime? = null,
    ) = OnGoingInteractiveFlowSession(
        id = this.id,
        purposes = purposes ?: this.purposes,
        flowId = this.flowId,
        expirationDate = this.expirationDate,
        sessionDate = this.sessionDate,
        userId = userId ?: this.userId,
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
