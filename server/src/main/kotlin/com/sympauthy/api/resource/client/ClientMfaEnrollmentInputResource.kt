package com.sympauthy.api.resource.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request body to start a standalone MFA enrollment on behalf of a signed-in end-user.")
@Serdeable
data class ClientMfaEnrollmentInputResource(
    @get:Schema(
        description = """
Access token identifying the end-user who initiated the enrollment.
Must be a valid, unexpired user access token issued by this authorization server to the calling client.
        """
    )
    @get:JsonProperty("access_token")
    val accessToken: String?,
    @get:Schema(
        description = """
URI the end-user is returned to once the MFA enrollment completes.
Must be one of the calling client's registered redirect URIs.
        """
    )
    @get:JsonProperty("return_uri")
    val returnUri: String?
)
