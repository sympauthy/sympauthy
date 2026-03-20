package com.sympauthy.api.resource.admin

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of users."
)
@Serdeable
data class AdminUserListResource(
    @get:Schema(description = "Array of user records.")
    val users: List<AdminUserResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of users matching the query.")
    val total: Int
)
