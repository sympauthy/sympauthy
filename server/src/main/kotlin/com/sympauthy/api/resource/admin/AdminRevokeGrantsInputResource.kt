package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

private const val NARROWS =
    "When absent, it does not narrow which grants are revoked; empty, it leaves none to revoke."

@Schema(
    description = "Request to revoke the grants it names. Each property narrows the grants revoked, and the " +
            "ones sent narrow together: a grant is revoked where it is one of `grant_ids`, held by one of " +
            "`user_ids`, opened for one of `client_ids` and for one of `audience_ids`, as far as each is sent. " +
            "At least one of them is sent."
)
@Serdeable
data class AdminRevokeGrantsInputResource(
    @get:Schema(description = "Identifiers of the grants to revoke. $NARROWS", nullable = true)
    @get:JsonProperty("grant_ids")
    val grantIds: List<UUID>?,
    @get:Schema(description = "Identifiers of the users whose grants to revoke. $NARROWS", nullable = true)
    @get:JsonProperty("user_ids")
    val userIds: List<UUID>?,
    @get:Schema(
        description = "Identifiers of the clients whose grants to revoke, which the configuration may no " +
                "longer declare. $NARROWS",
        nullable = true
    )
    @get:JsonProperty("client_ids")
    val clientIds: List<String>?,
    @get:Schema(
        description = "Identifiers of the audiences whose grants to revoke, which the configuration may no " +
                "longer declare. $NARROWS",
        nullable = true
    )
    @get:JsonProperty("audience_ids")
    val audienceIds: List<String>?
)
