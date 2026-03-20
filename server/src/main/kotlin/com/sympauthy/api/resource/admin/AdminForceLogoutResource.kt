package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(
    description = "Result of a force logout operation."
)
@Serdeable
data class AdminForceLogoutResource(
    @get:Schema(description = "Identifier of the user whose tokens were revoked.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(description = "Identifier of the client, or null if all clients were targeted.", nullable = true)
    @get:JsonProperty("client_id")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val clientId: String?,
    @get:Schema(description = "Number of tokens revoked.")
    @get:JsonProperty("tokens_revoked")
    val tokensRevoked: Int
)
