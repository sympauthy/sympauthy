package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Request body for an administrator to start an on-demand MFA enrollment for a given user."
)
@Serdeable
data class AdminUserMfaEnrollmentInputResource(
    @get:Schema(
        description = """
Identifier of the client the end-user will be returned into once the enrollment completes.
The ```return_uri``` (and optional ```cancel_uri```) are validated against this client's registered redirect
URIs, and the user is redirected back to it on completion.
        """
    )
    @get:JsonProperty("client_id")
    val clientId: String?,
    @get:Schema(
        description = """
URI the end-user is returned to once the MFA enrollment completes.
Must be one of the named client's registered redirect URIs.
        """
    )
    @get:JsonProperty("return_uri")
    val returnUri: String?,
    @get:Schema(
        description = """
Optional URI the end-user is returned to if they cancel the MFA enrollment.
When provided, must be one of the named client's registered redirect URIs. When omitted, the enrollment
offers no cancellation.
        """
    )
    @get:JsonProperty("cancel_uri")
    val cancelUri: String? = null
)
