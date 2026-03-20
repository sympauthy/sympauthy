package com.sympauthy.api.resource.admin

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of user claims."
)
@Serdeable
data class AdminUserClaimListResource(
    @get:Schema(description = "Array of claim records.")
    val claims: List<AdminUserClaimResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of claims matching the filters.")
    val total: Int
)
