package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.*

@Schema(
    description = "One interactive flow session, every purpose it carries and where each one stands."
)
@Serdeable
data class AdminInteractiveFlowSessionDetailResource(
    @get:Schema(description = "Unique identifier of the session.")
    @get:JsonProperty("id")
    val id: UUID,
    @get:Schema(
        description = "What became of the session. 'expired' means it was still ongoing when its expiration " +
                "passed — nobody finished it. Both 'failed' and 'expired' ended in a failure and carry the " +
                "five error fields below; an expired session's are the ones the expiry itself names, since " +
                "no rule refused anything.",
        allowableValues = ["ongoing", "completed", "cancelled", "failed", "expired"]
    )
    @get:JsonProperty("status")
    val status: String,
    @get:Schema(description = "The purpose that started the session and owns its terminal handoff.")
    @get:JsonProperty("initiating_purpose")
    val initiatingPurpose: AdminInteractiveFlowPurposeResource,
    @get:Schema(
        description = "Identifier of the client the session was started for. Absent where an administrator " +
                "started it and where nothing named a client."
    )
    @get:JsonProperty("client_id")
    val clientId: String?,
    @get:Schema(description = "Identifier of the interactive flow the person is going through.")
    @get:JsonProperty("flow_id")
    val flowId: String?,
    @get:Schema(
        description = "The account the session identified. Absent where it identified nobody, and where the " +
                "account it identified is one this session is still signing up."
    )
    @get:JsonProperty("user")
    val user: AdminUserResource?,
    @get:Schema(description = "Whether the account was created during this session.")
    @get:JsonProperty("signed_up")
    val signedUp: Boolean,
    @get:Schema(description = "Date and time (in UTC timezone) at which the session started.")
    @get:JsonProperty("session_date")
    val sessionDate: LocalDateTime,
    @get:Schema(description = "Date and time (in UTC timezone) at which the session expires.")
    @get:JsonProperty("expiration_date")
    val expirationDate: LocalDateTime,
    @get:Schema(
        description = "Identifier of the message detailing, technically, what the session failed with. " +
                "It is the key itself, so it can be searched for and branched on; the sentence it names is " +
                "beside it. Absent unless the session ended in a failure."
    )
    @get:JsonProperty("error_details_id")
    val errorDetailsId: String?,
    @get:Schema(
        description = "The message error_details_id names, read in the language the request asked for, with " +
                "the error values interpolated into it. It is written for whoever operates this server and " +
                "may name a row, a claim, a provider or a key; it may be reworded in any release, so branch " +
                "on error_details_id instead. Absent unless the session ended in a failure, and absent " +
                "where this deployment holds no message under that identifier."
    )
    @get:JsonProperty("error_details")
    val errorDetails: String?,
    @get:Schema(
        description = "Identifier of the message the end-user was shown. It is the failure's own where it " +
                "names one and the generic message's identifier otherwise, so it names what the person was " +
                "actually told rather than what the failure asked for. It is the key itself, so it can be " +
                "searched for and branched on. Absent unless the session ended in a failure."
    )
    @get:JsonProperty("error_description_id")
    val errorDescriptionId: String?,
    @get:Schema(
        description = "The message error_description_id names, read in the language the request asked for " +
                "rather than the end-user's, with the error values interpolated into it. It is rendered the " +
                "way the flow's error page rendered it, so it is the sentence that was on the screen. It " +
                "may be reworded in any release, so branch on error_description_id instead. Absent unless " +
                "the session ended in a failure, and absent where this deployment holds no message under " +
                "that identifier."
    )
    @get:JsonProperty("error_description")
    val errorDescription: String?,
    @get:Schema(
        description = "Values interpolated into the two messages, published raw as well so a reader can show " +
                "one as a field of its own. Absent unless the session ended in a failure."
    )
    @get:JsonProperty("error_values")
    val errorValues: Map<String, String>?,
    @get:Schema(description = "Every purpose the session carries, in the order it drives them.")
    @get:JsonProperty("purposes")
    val purposes: List<AdminInteractiveFlowSessionPurposeProgressResource>
)
