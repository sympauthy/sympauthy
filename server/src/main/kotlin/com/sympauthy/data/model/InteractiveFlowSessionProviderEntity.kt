package com.sympauthy.data.model

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.serde.annotation.Serdeable
import java.util.*

@Serdeable
@MappedEntity("interactive_flow_session_provider")
data class InteractiveFlowSessionProviderEntity(
    @get:Id
    val sessionId: UUID,
    val providerId: String,
    val providerNonceJsonWebTokenId: UUID? = null,
)
