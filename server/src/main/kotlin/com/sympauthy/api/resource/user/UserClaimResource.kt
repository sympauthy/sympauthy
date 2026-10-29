package com.sympauthy.api.resource.user

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Claims of the end-user the access token was issued for, that they may read through it."
)
@Serdeable
data class UserClaimResource(
    @get:Schema(
        description = "Values of the claims the end-user may read, including those this server generates such " +
            "as `sub`, keyed by claim identifier."
    )
    val claims: Map<String, Any?>
)
