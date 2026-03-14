package com.sympauthy.api.resource.admin

import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of consents."
)
@Serdeable
data class AdminConsentListResource(
    val consents: List<AdminConsentResource>,
    val page: Int,
    val size: Int,
    val total: Int
)
