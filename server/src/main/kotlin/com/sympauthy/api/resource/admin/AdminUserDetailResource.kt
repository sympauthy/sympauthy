package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "A user, with the values of its identifier claims."
)
@Serdeable
data class AdminUserDetailResource(
    @get:Schema(
        description = "Identifier of the user."
    )
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(
        description = "Status of the user.",
        allowableValues = ["enabled", "disabled"]
    )
    val status: String,
    @get:Schema(
        description = "Date and time at which the user was created, in UTC."
    )
    @get:JsonProperty("created_at")
    val createdAt: LocalDateTime,
    @get:Schema(
        description = "Values the user holds for the identifier claims, the claims an end-user signs in " +
                "with, keyed by claim identifier. An identifier claim the user holds no value for is left out."
    )
    @get:JsonProperty("identifier_claims")
    val identifierClaims: Map<String, Any?>
)
