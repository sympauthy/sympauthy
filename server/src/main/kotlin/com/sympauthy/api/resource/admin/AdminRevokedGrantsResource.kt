package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "What a revocation of grants took out of service.")
@Serdeable
data class AdminRevokedGrantsResource(
    @get:Schema(
        description = "Number of grants this call revoked, every token issued under each of them with it, " +
                "including the grants a client obtained by exchanging one of their tokens. A grant no longer " +
                "`live` is left as it was and not counted, so a second call answers `0`."
    )
    @get:JsonProperty("grants_revoked")
    val grantsRevoked: Int
)
