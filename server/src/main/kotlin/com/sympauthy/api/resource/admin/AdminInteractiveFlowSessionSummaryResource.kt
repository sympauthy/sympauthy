package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "An interactive flow session, as the collection lists it."
)
@Serdeable
data class AdminInteractiveFlowSessionSummaryResource(
    @get:Schema(description = "Identifier of the interactive flow session.")
    @get:JsonProperty("id")
    val id: UUID,
    @get:Schema(
        description = "What became of the interactive flow session. `expired` is a session still ongoing " +
                "when its `expiration_date` passed.",
        allowableValues = ["ongoing", "completed", "cancelled", "failed", "expired"]
    )
    @get:JsonProperty("status")
    val status: String,
    @get:Schema(
        description = "Purpose the interactive flow session was started for, as the `purpose` criterion of " +
                "this collection takes it.",
        allowableValues = ["oauth2_authorize", "mfa_enrollment", "link_provider"]
    )
    @get:JsonProperty("initiating_purpose")
    val initiatingPurpose: String,
    @get:Schema(
        description = "Purpose the end-user is at. Absent when the session completed, failed or was " +
                "cancelled, when every purpose is completed, and when it cannot be determined.",
        allowableValues = [
            "confirm", "oauth2_authorize", "mfa_enrollment", "mfa_challenge", "reauthentication", "link_provider"
        ]
    )
    @get:JsonProperty("current_purpose")
    val currentPurpose: String?,
    @get:Schema(
        description = "Client the interactive flow session was started for, which the configuration may no " +
                "longer declare. Absent when an administrator started it, and when the authorization request " +
                "that started it named no valid client."
    )
    @get:JsonProperty("client")
    val client: AdminClientReferenceResource?,
    @get:Schema(
        description = "Whether the end-user signed up during this interactive flow session. While the sign-up " +
                "is in progress, `user` is absent."
    )
    @get:JsonProperty("signed_up")
    val signedUp: Boolean,
    @get:Schema(
        description = "User the interactive flow session identified, with only its identifier claims. Absent " +
                "when the session identified no user, while its sign-up is in progress, and when the user no " +
                "longer exists."
    )
    @get:JsonProperty("user")
    val user: AdminUserResource?,
    @get:Schema(
        description = "Address the latest request of the interactive flow session came from. Absent when no " +
                "request was recorded, which is the case once the session completed."
    )
    @get:JsonProperty("ip")
    val ip: String?,
    @get:Schema(
        description = "User agent the latest request of the interactive flow session sent. Absent when that " +
                "request sent none, and when `ip` is absent."
    )
    @get:JsonProperty("user_agent")
    val userAgent: String?,
    @get:Schema(description = "Date and time at which the interactive flow session started, in UTC.")
    @get:JsonProperty("session_date")
    val sessionDate: LocalDateTime,
    @get:Schema(description = "Date and time at which the interactive flow session expires, in UTC.")
    @get:JsonProperty("expiration_date")
    val expirationDate: LocalDateTime
)
