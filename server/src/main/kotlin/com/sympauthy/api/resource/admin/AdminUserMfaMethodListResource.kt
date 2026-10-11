package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import com.sympauthy.api.util.PAGE_PROPERTY_DESCRIPTION
import com.sympauthy.api.util.SIZE_PROPERTY_DESCRIPTION
import com.sympauthy.api.util.TOTAL_PROPERTY_DESCRIPTION
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of MFA methods."
)
@Serdeable
data class AdminUserMfaMethodListResource(
    @get:Schema(description = "The MFA methods on this page.")
    @get:JsonProperty("mfa_methods")
    val mfaMethods: List<AdminUserMfaMethodResource>,
    @get:Schema(description = PAGE_PROPERTY_DESCRIPTION)
    val page: Int,
    @get:Schema(description = SIZE_PROPERTY_DESCRIPTION)
    val size: Int,
    @get:Schema(description = TOTAL_PROPERTY_DESCRIPTION)
    val total: Int
)
