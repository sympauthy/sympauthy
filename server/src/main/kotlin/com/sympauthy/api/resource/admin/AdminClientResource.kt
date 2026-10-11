package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "A client the configuration declares, without its secret."
)
@Serdeable
data class AdminClientResource(
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
    @get:ArraySchema(
        arraySchema = Schema(
            description = "Grant types the client may use."
        ),
        schema = Schema(
            allowableValues = [
                "authorization_code",
                "refresh_token",
                "client_credentials",
                "urn:ietf:params:oauth:grant-type:token-exchange"
            ]
        )
    )
    @get:JsonProperty("allowed_grant_types")
    val allowedGrantTypes: List<String>,
    @get:Schema(
        description = "Identifier of the flow an end-user goes through to authorize the client. Absent when " +
                "the configuration names none for the client, and the default flow is used."
    )
    @get:JsonProperty("authorization_flow_id")
    val authorizationFlowId: String?,
    @get:Schema(
        description = "Scopes the client may request. Empty both when the configuration does not restrict " +
                "them and when it allows none."
    )
    @get:JsonProperty("allowed_scopes")
    val allowedScopes: List<String>,
    @get:Schema(
        description = "Scopes the client is given when its authorization request names none."
    )
    @get:JsonProperty("default_scopes")
    val defaultScopes: List<String>,
    @get:Schema(
        description = "URLs the client may send the end-user back to after authorization. Empty when the " +
                "client may not use the `authorization_code` grant."
    )
    @get:JsonProperty("allowed_redirect_uris")
    val allowedRedirectUris: List<String>,
    @get:Schema(
        description = "Webhook this server calls to decide which scopes to grant the client. Absent when none " +
                "is configured."
    )
    @get:JsonProperty("authorization_webhook")
    val authorizationWebhook: AdminAuthorizationWebhookResource?
)
