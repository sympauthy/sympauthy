package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "Information about a user."
)
@Serdeable
data class AdminUserResource(
    @get:Schema(
        description = "Unique identifier of the user."
    )
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(
        description = "Status of the user."
    )
    val status: String,
    @get:Schema(
        description = "The date and time (in UTC timezone) at which the user has been created."
    )
    @get:JsonProperty("created_at")
    val createdAt: LocalDateTime,
    @get:Schema(
        description = "Claims associated to the user. Keys are claim identifiers, values are claim values. Null when claims are not requested.",
        nullable = true
    )
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val claims: Map<String, Any?>? = null
)
