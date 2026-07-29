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
    @MappedProperty(type = DataType.JSON)
    val purposes: List<String>,
    val sessionDate: LocalDateTime,
    val flowId: String? = null,
    val expirationDate: LocalDateTime,

    // User identification
    val userId: UUID? = null,

    // MFA
    val mfaPassedDate: LocalDateTime? = null,

    // Completion
    val completeDate: LocalDateTime? = null,

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
