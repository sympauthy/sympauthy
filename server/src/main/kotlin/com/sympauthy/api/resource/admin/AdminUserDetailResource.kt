package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

internal const val USER_LAST_AUTHENTICATED_AT_PROPERTY_DESCRIPTION =
        "Date and time at which the end-user last proved a credential in an interactive flow that then " +
        "completed, in UTC. Where that flow was an authorization, it is the `auth_time` of the tokens it led " +
        "to. Absent when the end-user has never authenticated"

internal const val USER_LAST_REFRESHED_AT_PROPERTY_DESCRIPTION =
        "Date and time at which a grant held for the user was last refreshed, in UTC. A refresh is a client " +
        "acting alone, so it says nothing of the end-user being present. Absent when no grant of the user has " +
        "been refreshed"

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
    @get:Schema(description = "$USER_LAST_AUTHENTICATED_AT_PROPERTY_DESCRIPTION.")
    @get:JsonProperty("last_authenticated_at")
    val lastAuthenticatedAt: LocalDateTime?,
    @get:Schema(description = "$USER_LAST_REFRESHED_AT_PROPERTY_DESCRIPTION.")
    @get:JsonProperty("last_refreshed_at")
    val lastRefreshedAt: LocalDateTime?,
    @get:Schema(
        description = "Values the user holds for the identifier claims, the claims an end-user signs in " +
                "with, keyed by claim identifier. An identifier claim the user holds no value for is left out."
    )
    @get:JsonProperty("identifier_claims")
    val identifierClaims: Map<String, Any?>
)
