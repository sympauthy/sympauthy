package com.sympauthy.api.resource.admin

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
        description = "Uniq identifier of the user."
    )
    val id: UUID,
    @get:Schema(
        description = "The date and time (in UTC timezone) at which the user has been created."
    )
    @get:JsonProperty("creation_date")
    val creationDate: LocalDateTime
)
