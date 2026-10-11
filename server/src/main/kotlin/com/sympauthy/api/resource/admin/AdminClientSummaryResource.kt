package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A client the configuration declares, as the collection lists it."
)
@Serdeable
data class AdminClientSummaryResource(
    @get:Schema(description = "Identifier of the client.")
    @get:JsonProperty("client_id")
    val clientId: String,
    @get:Schema(
        description = "Type of the client. A `public` client holds no secret and must use PKCE.",
        allowableValues = ["public", "confidential"]
    )
    val type: String,
    @get:Schema(description = "Identifier of the audience the client belongs to.")
    @get:JsonProperty("audience_id")
    val audienceId: String,
    @get:Schema(
        description = "URLs the client may send the end-user back to after authorization. Empty when the " +
                "client may not use the `authorization_code` grant."
    )
    @get:JsonProperty("allowed_redirect_uris")
    val allowedRedirectUris: List<String>
)
