package com.sympauthy.api.resource.admin

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of interactive flow sessions."
)
@Serdeable
data class AdminInteractiveFlowSessionListResource(
    @get:Schema(description = "Array of interactive flow sessions.")
    val sessions: List<AdminInteractiveFlowSessionSummaryResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of sessions the criteria kept.")
    val total: Int
)
