package com.sympauthy.api.resource.admin

import com.sympauthy.api.util.PAGE_PROPERTY_DESCRIPTION
import com.sympauthy.api.util.SIZE_PROPERTY_DESCRIPTION
import com.sympauthy.api.util.TOTAL_PROPERTY_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of consents."
)
@Serdeable
data class AdminConsentListResource(
    @get:Schema(description = "The consents on this page.")
    val consents: List<AdminConsentResource>,
    @get:Schema(description = PAGE_PROPERTY_DESCRIPTION)
    val page: Int,
    @get:Schema(description = SIZE_PROPERTY_DESCRIPTION)
    val size: Int,
    @get:Schema(description = TOTAL_PROPERTY_DESCRIPTION)
    val total: Int
)
