package com.sympauthy.api.resource.admin

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of audiences."
)
@Serdeable
data class AdminAudienceListResource(
    @get:Schema(description = "Array of audience records.")
    val audiences: List<AdminAudienceResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of audiences.")
    val total: Int
)
