package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "A user, with the values it holds for some of the claims."
)
@Serdeable
data class AdminUserResource(
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
        description = "Values the user holds, keyed by claim identifier. In the user collection, the " +
                "claims the `claims` parameter selected, a selected claim the user holds no value for " +
                "being null. In an interactive flow session, the identifier claims the user holds a " +
                "value for. Null when `claims` is sent empty.",
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val claims: Map<String, Any?>? = null
)
