package com.sympauthy.api.controller.flow

import com.sympauthy.api.mapper.flow.TimeZoneResourceMapper
import com.sympauthy.api.resource.provider.TimeZoneResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.TimeZoneClaimValueProvider
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/api/v1/flow/claims/{claimId}/timezones")
class TimeZoneController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val timeZoneProvider: TimeZoneClaimValueProvider,
    @Inject private val timeZoneResourceMapper: TimeZoneResourceMapper
) {

    @Get
    @Operation(
        description = "List the time zones the end-user can pick for a claim: those the claim allows, or every " +
            "time zone this server knows when the claim allows any. Time zones are ordered by identifier.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The time zones the end-user can pick.",
                useReturnTypeSchema = true
            ),
            ApiResponse(responseCode = "404", description = "- `not_found`: `claimId` names no enabled claim.")
        ],
        tags = ["flow"]
    )
    suspend fun listTimeZones(
        @PathVariable @Parameter(description = "Identifier of the claim.") claimId: String
    ): List<TimeZoneResource> {
        val claim = claimManager.findByIdOrNull(claimId).orNotFound()
        val timeZones = timeZoneProvider.listAvailableTimezones(claim)
        return timeZoneResourceMapper.toResources(timeZones)
    }
}
