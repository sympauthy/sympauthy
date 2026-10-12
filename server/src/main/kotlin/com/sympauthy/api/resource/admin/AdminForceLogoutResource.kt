package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(
    description = "The tokens revoked for a user."
)
@Serdeable
data class AdminForceLogoutResource(
    @get:Schema(description = "Identifier of the user whose tokens were revoked.")
    @get:JsonProperty("user_id")
    val userId: UUID,
    @get:Schema(
        description = "Identifier of the client whose tokens were revoked. Null when the tokens of every " +
                "client were revoked.",
        nullable = true
    )
    @get:JsonProperty("client_id")
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    val clientId: String?,
    @get:Schema(
        description = "Number of tokens this call revoked. A token already revoked or expired is left as " +
                "it was and not counted, so a second call answers `0`."
    )
    @get:JsonProperty("tokens_revoked")
    val tokensRevoked: Int
)
