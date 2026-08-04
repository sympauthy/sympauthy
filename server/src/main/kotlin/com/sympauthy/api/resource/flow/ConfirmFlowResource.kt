package com.sympauthy.api.resource.flow

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = """
Everything the custom UI needs to render the confirmation step, where the signed-in end-user must explicitly
approve an action a client (or an administrator) initiated on their behalf before the flow proceeds.

This resource returns either:
- the context of the confirmation step (```action```, ```initiating_client_id```), which the custom UI turns
  into localized copy describing what is being approved and who requested it.
- a ```redirect_url``` where the end-user must be redirected to continue the flow (e.g. once already
  confirmed, or when the session carries no confirmation step). When set, all other fields are null.

The end-user approves by calling ```POST /api/v1/flow/confirm``` or denies by cancelling the flow via
```POST /api/v1/flow/cancel```.

Every URL contained in this resource already includes the ```state``` query param.
"""
)
@Serdeable
data class ConfirmFlowResource(
    @get:Schema(
        description = """
Machine-readable code of the action the end-user is asked to approve (e.g. ```ENROLL_MFA```). Null when this
resource carries a ```redirect_url```.
        """
    )
    val action: String? = null,
    @get:Schema(
        name = "requires_reauthentication",
        description = """
Whether the end-user will be asked to re-authenticate (prove they own the account) after approving this
action, so the custom UI can warn them up front. Computed from the session's remaining steps: true when a
re-authentication step is still ahead (e.g. linking a provider), false otherwise (e.g. MFA enrollment).
Defaults to false; irrelevant when this resource carries a ```redirect_url```.
        """
    )
    @get:JsonProperty("requires_reauthentication")
    val requiresReauthentication: Boolean = false,
    @get:Schema(
        name = "initiating_client_id",
        description = """
Identifier of the client that initiated the action on the end-user's behalf. Null when the action was
initiated by an administrator (the custom UI should then render a generic initiator such as
"an administrator"), or when this resource carries a ```redirect_url```.
        """
    )
    @get:JsonProperty("initiating_client_id")
    val initiatingClientId: String? = null,
    @get:Schema(
        name = "redirect_url",
        description = """
URL where the end-user must be redirected to continue the flow instead of rendering the confirmation step.
When set, all other fields are null.
        """
    )
    @get:JsonProperty("redirect_url")
    val redirectUrl: String? = null
)
