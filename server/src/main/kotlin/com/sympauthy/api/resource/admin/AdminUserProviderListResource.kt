package com.sympauthy.api.resource.admin

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of providers linked to a user."
)
@Serdeable
data class AdminUserProviderListResource(
    @get:Schema(description = "Array of linked provider records.")
    val providers: List<AdminUserProviderResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of providers linked to this user.")
    val total: Int
)
