package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.util.*

@Serdeable
@MappedEntity("interactive_flow_session_mfa_enrollment")
data class InteractiveFlowSessionMfaEnrollmentEntity(
    @get:Id
    val sessionId: UUID,
    val returnUri: String,
)
