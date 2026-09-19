package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "One interactive flow session, as the collection shows it."
)
@Serdeable
data class AdminInteractiveFlowSessionSummaryResource(
    @get:Schema(description = "Unique identifier of the session.")
    @get:JsonProperty("id")
    val id: UUID,
    @get:Schema(
        description = "What became of the session. 'expired' means it was still ongoing when its expiration " +
                "passed — nobody finished it — and never relabels a session that completed, cancelled or " +
                "failed first.",
        allowableValues = ["ongoing", "completed", "cancelled", "failed", "expired"]
    )
    @get:JsonProperty("status")
    val status: String,
    @get:Schema(description = "The purpose that started the session and owns its terminal handoff.")
    @get:JsonProperty("initiating_purpose")
    val initiatingPurpose: AdminInteractiveFlowPurposeResource,
    @get:Schema(
        description = "The purpose the session is stopped at. Absent once every purpose has resolved, and " +
                "for a session that completed, cancelled or failed."
    )
    @get:JsonProperty("current_purpose")
    val currentPurpose: AdminInteractiveFlowPurposeResource?,
    @get:Schema(
        description = "Identifier of the client the session was started for. Absent where an administrator " +
                "started it and where nothing named a client. It may name a client this deployment no longer " +
                "declares, since editing the configuration does not rewrite sessions already in flight."
    )
    @get:JsonProperty("client_id")
    val clientId: String?,
    @get:Schema(
        description = "Whether the account was created during this session. A session signing an account up " +
                "publishes no user until it completes, so this is what tells a sign-up in progress apart " +
                "from a person who has not identified themselves at all."
    )
    @get:JsonProperty("signed_up")
    val signedUp: Boolean,
    @get:Schema(
        description = "The account the session identified. Absent where it identified nobody, and where the " +
                "account it identified is one this session is still signing up."
    )
    @get:JsonProperty("user")
    val user: AdminUserResource?,
    @get:Schema(
        description = "Address the session was last driven from. Absent where nothing was recorded against " +
                "it; the places before this one are on the session's own page."
    )
    @get:JsonProperty("ip")
    val ip: String?,
    @get:Schema(description = "User agent observed alongside the address. Absent for the same reason.")
    @get:JsonProperty("user_agent")
    val userAgent: String?,
    @get:Schema(description = "Date and time (in UTC timezone) at which the session started.")
    @get:JsonProperty("session_date")
    val sessionDate: LocalDateTime,
    @get:Schema(description = "Date and time (in UTC timezone) at which the session expires.")
    @get:JsonProperty("expiration_date")
    val expirationDate: LocalDateTime
)
