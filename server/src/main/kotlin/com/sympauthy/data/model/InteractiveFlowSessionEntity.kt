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
    // Session metadata
    val purposes: Array<String>,
    val sessionDate: LocalDateTime,
    val flowId: String? = null,
    val expirationDate: LocalDateTime,

    // User identification
    val userId: UUID? = null,
    val signedUp: Boolean = false,

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
