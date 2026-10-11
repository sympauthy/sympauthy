package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request to start an MFA enrollment for an end-user signed in to this client.")
@Serdeable
data class ClientMfaEnrollmentInputResource(
    @get:Schema(
        description = "Access token this server issued to this client for the end-user, still active. The MFA " +
            "method is enrolled for the user it was issued for. When absent, the request is refused."
    )
    @get:JsonProperty("access_token")
    val accessToken: String?,
    @get:Schema(
        description = "URL the end-user's browser is sent to once the enrollment completes. It must be one of " +
            "this client's `clients.<id>.allowed-redirect-uris`. When absent, the request is refused."
    )
    @get:JsonProperty("return_uri")
    val returnUri: String?,
    @get:Schema(
        description = "URL the end-user's browser is sent to if the end-user cancels the enrollment. It must be " +
            "one of this client's `clients.<id>.allowed-redirect-uris`. When absent, the enrollment cannot be " +
            "cancelled."
    )
    @get:JsonProperty("cancel_uri")
    val cancelUri: String? = null
)
