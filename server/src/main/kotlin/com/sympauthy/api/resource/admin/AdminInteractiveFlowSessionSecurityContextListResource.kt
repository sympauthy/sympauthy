package com.sympauthy.api.resource.admin

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Paginated list of the places one interactive flow session was driven from."
)
@Serdeable
data class AdminInteractiveFlowSessionSecurityContextListResource(
    @get:Schema(description = "Array of places, the one seen most recently first.")
    @get:JsonProperty("security_contexts")
    val securityContexts: List<AdminInteractiveFlowSessionSecurityContextResource>,
    @get:Schema(description = "Current page number.")
    val page: Int,
    @get:Schema(description = "Number of results per page.")
    val size: Int,
    @get:Schema(description = "Total number of places the session holds.")
    val total: Int
)
