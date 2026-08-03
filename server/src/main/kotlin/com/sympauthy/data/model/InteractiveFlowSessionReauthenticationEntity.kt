package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.time.LocalDateTime
import java.util.*

@Serdeable
@MappedEntity("interactive_flow_session_reauthentication")
data class InteractiveFlowSessionReauthenticationEntity(
    @get:Id
    val sessionId: UUID,
    val primaryCredentialProvenDate: LocalDateTime? = null,
)
