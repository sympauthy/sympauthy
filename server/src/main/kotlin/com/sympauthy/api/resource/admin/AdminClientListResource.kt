package com.sympauthy.api.resource.admin

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of clients."
)
@Serdeable
data class AdminClientListResource(
    @get:Schema(description = "Array of client records.")
    val clients: List<AdminClientResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of clients.")
    val total: Int
)
