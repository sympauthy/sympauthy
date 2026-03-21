package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of MFA methods."
)
@Serdeable
data class AdminUserMfaMethodListResource(
    @get:Schema(description = "Array of registered MFA method records.")
    @get:JsonProperty("mfa_methods")
    val mfaMethods: List<AdminUserMfaMethodResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of registered MFA methods for this user.")
    val total: Int
)
