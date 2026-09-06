package com.sympauthy.data.model

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.DataType
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("interactive_flow_sessions")
class InteractiveFlowSessionEntity(
    // Optimistic-concurrency counter. Incremented by every guarded lifecycle update in
    // InteractiveFlowSessionRepository (WHERE version = :expectedVersion ... SET version = version + 1).
    // Deliberately a plain column, not @Version: Micronaut's optimistic locking only engages on
    // full-entity update/delete, whereas the session is mutated through query-based partial updates.
    val version: Long = 0,

    // Session metadata
    val purposes: Array<String>,
    val initiatingPurpose: String,
    val sessionDate: LocalDateTime,
    val flowId: String? = null,
    val expirationDate: LocalDateTime,

    // User identification
    val userId: UUID? = null,
    val signedUp: Boolean = false,

    /**
     * Every security context this session has been seen in, in the order they were first observed,
     * so the first of them is the one it was initiated from.
     *
     * There is no foreign key. A context is collected on a retention of its own — a day where no user
     * was ever attached to it — while a session outlives nothing, so a key here would make that sweep
     * fail against a deployment whose retention is shorter than its sessions rather than delete.
     *
     * The two columns are written outside the version guard the lifecycle mutations use: a request
     * arriving from a new place is not a step of the flow, and making it one would fail a person's
     * sign-in over a race that costs a row nothing points at.
     */
    val securityContextIds: Array<UUID> = emptyArray(),
    /**
     * The one the last request arrived from, which is not the last of [securityContextIds]: a flow
     * returning to a place it has already been seen in points back at the row it already wrote.
     */
    val currentSecurityContextId: UUID? = null,

    // MFA
    val mfaPassedDate: LocalDateTime? = null,

    // Terminal redirect: where and how the end-user is handed back to the flow's initiator.
    val successRedirectUri: String? = null,
    val redirectType: String? = null,
    val cancelRedirectUri: String? = null,

    // Completion
    val completedPurposes: Array<String> = emptyArray(),
    val completeDate: LocalDateTime? = null,

    // Cancellation
    val cancelDate: LocalDateTime? = null,

    // Error
    val errorDate: LocalDateTime? = null,
    val errorDetailsId: String? = null,
    val errorDescriptionId: String? = null,
    @MappedProperty(type = DataType.JSON)
    val errorValues: Map<String, String>? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
