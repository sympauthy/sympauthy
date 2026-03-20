package com.sympauthy.api.resource.admin

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of scopes."
)
@Serdeable
data class AdminScopeListResource(
    @get:Schema(description = "Array of scope records.")
    val scopes: List<AdminScopeResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of scopes.")
    val total: Int
)
