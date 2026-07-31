package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("interactive_flow_session_confirm")
data class InteractiveFlowSessionConfirmEntity(
    @get:Id
    val sessionId: UUID,
    val action: String,
    val clientId: String? = null,
    val confirmedDate: LocalDateTime? = null,
)
