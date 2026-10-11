package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

private const val ABSENT_UNLESS_FAILED = "Absent unless `status` is `failed` or `expired`"

@Schema(
    description = "An interactive flow session, with each of its purposes and how far the session got with it."
)
@Serdeable
data class AdminInteractiveFlowSessionDetailResource(
    @get:Schema(description = "Identifier of the interactive flow session.")
    @get:JsonProperty("id")
    val id: UUID,
    @get:Schema(
        description = "What became of the interactive flow session. `expired` is a session still ongoing " +
                "when its `expiration_date` passed. A `failed` or `expired` session carries the error fields.",
        allowableValues = ["ongoing", "completed", "cancelled", "failed", "expired"]
    )
    @get:JsonProperty("status")
    val status: String,
    @get:Schema(description = "Purpose the interactive flow session was started for.")
    @get:JsonProperty("initiating_purpose")
    val initiatingPurpose: AdminInteractiveFlowPurposeResource,
    @get:Schema(
        description = "Identifier of the client the interactive flow session was started for. Absent when an " +
                "administrator started it, and when the authorization request that started it named no valid " +
                "client."
    )
    @get:JsonProperty("client_id")
    val clientId: String?,
    @get:Schema(description = "Identifier of the flow the end-user goes through.")
    @get:JsonProperty("flow_id")
    val flowId: String?,
    @get:Schema(
        description = "User the interactive flow session identified, with only its identifier claims. Absent " +
                "when the session identified no user, while its sign-up is in progress, and when the user no " +
                "longer exists."
    )
    @get:JsonProperty("user")
    val user: AdminUserResource?,
    @get:Schema(
        description = "Whether the end-user signed up during this interactive flow session. While the sign-up " +
                "is in progress, `user` is absent."
    )
    @get:JsonProperty("signed_up")
    val signedUp: Boolean,
    @get:Schema(description = "Date and time at which the interactive flow session started, in UTC.")
    @get:JsonProperty("session_date")
    val sessionDate: LocalDateTime,
    @get:Schema(description = "Date and time at which the interactive flow session expires, in UTC.")
    @get:JsonProperty("expiration_date")
    val expirationDate: LocalDateTime,
    @get:Schema(
        description = "Error code the interactive flow session ended with. $ABSENT_UNLESS_FAILED."
    )
    @get:JsonProperty("error_details_id")
    val errorDetailsId: String?,
    @get:Schema(
        description = "Technical message `error_details_id` names, in the language the request asked for. It " +
                "may name a claim, a provider or a configuration key, and may be reworded in any release. " +
                "$ABSENT_UNLESS_FAILED, and when this server holds no message for that code."
    )
    @get:JsonProperty("error_details")
    val errorDetails: String?,
    @get:Schema(
        description = "Identifier of the message the end-user was shown on the error page: the failure's own, " +
                "or the generic one when the failure names none. $ABSENT_UNLESS_FAILED."
    )
    @get:JsonProperty("error_description_id")
    val errorDescriptionId: String?,
    @get:Schema(
        description = "Message `error_description_id` names, as the error page showed it to the end-user but " +
                "in the language the request asked for. It may be reworded in any release. " +
                "$ABSENT_UNLESS_FAILED, and when this server holds no message for that identifier."
    )
    @get:JsonProperty("error_description")
    val errorDescription: String?,
    @get:Schema(
        description = "Values interpolated into `error_details` and `error_description`, keyed by name. " +
                "$ABSENT_UNLESS_FAILED."
    )
    @get:JsonProperty("error_values")
    val errorValues: Map<String, String>?,
    @get:Schema(
        description = "Purposes of the interactive flow session, in the order it takes the end-user through them."
    )
    @get:JsonProperty("purposes")
    val purposes: List<AdminInteractiveFlowSessionPurposeProgressResource>
)
