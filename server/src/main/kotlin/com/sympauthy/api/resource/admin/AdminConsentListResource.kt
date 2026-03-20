package com.sympauthy.api.resource.admin

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of consents."
)
@Serdeable
data class AdminConsentListResource(
    @get:Schema(description = "Array of consent records.")
    val consents: List<AdminConsentResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of active consents for this user.")
    val total: Int
)
