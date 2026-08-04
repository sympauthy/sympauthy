package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.util.*

@Serdeable
@MappedEntity("interactive_flow_session_link_provider")
data class InteractiveFlowSessionLinkProviderEntity(
    @get:Id
    val sessionId: UUID,
    val providerId: String,
)
