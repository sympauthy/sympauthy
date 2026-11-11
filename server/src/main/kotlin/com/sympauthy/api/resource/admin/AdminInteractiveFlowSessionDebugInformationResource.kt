package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "One value an interactive flow session holds for a purpose, for debugging."
)
@Serdeable
data class AdminInteractiveFlowSessionDebugInformationResource(
    @get:Schema(
        description = "Code naming the value, unique within the purpose. A code is never renamed, and a new " +
                "one may appear in any release. The codes each purpose answers with are:\n\n" +
                "- `oauth2_authorize`: `client_id`, `redirect_uri`, `requested_scopes`, `consented_scopes`, " +
                "`consented_at`, `consented_by`, `granted_scopes`, `granted_at`, `granted_by`, `invitation_id`, " +
                "`state`, `nonce`, `code_challenge`, `code_challenge_method`.\n" +
                "- `confirm`: `action`, `initiated_by`, `client_id`, `confirmed_at`.\n" +
                "- `mfa_enrollment`: `enrolled_methods`, `mfa_passed_at`.\n" +
                "- `mfa_challenge`: `mfa_passed_at`, `challengeable_methods`.\n" +
                "- `reauthentication`: `primary_credential_proven_at`.\n" +
                "- `link_provider`: `target_provider_id`, `authorizing_provider_id`."
    )
    @get:JsonProperty("code")
    val code: String,
    @get:Schema(
        description = "How `value` is written. `text` is a string, `text_list` an array of strings, `date` a " +
                "date and time in UTC, and `presence` a boolean saying whether the interactive flow session " +
                "holds a value this server does not publish.",
        allowableValues = ["text", "text_list", "date", "presence"]
    )
    @get:JsonProperty("type")
    val type: String,
    @get:Schema(
        description = "The value, written as `type` says. Absent when it is not set; the entry is listed all " +
                "the same."
    )
    @get:JsonProperty("value")
    val value: Any?
)

