package com.sympauthy.api.controller.flow

import com.sympauthy.api.controller.FLOW_SURFACE
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.mapper.flow.TimeZoneResourceMapper
import com.sympauthy.api.resource.provider.TimeZoneResource
import com.sympauthy.api.util.FLOW_ONGOING_SESSION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION
import com.sympauthy.api.util.FLOW_STATE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.manager.user.TimeZoneClaimValueProvider
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.business.model.user.claim.ClaimDataType
import com.sympauthy.security.SecurityRule.HAS_STATE
import com.sympauthy.security.stateOrNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Secured(HAS_STATE)
@Controller("$FLOW_SURFACE/claims/{claimId}/timezones")
class TimeZoneController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val timeZoneProvider: TimeZoneClaimValueProvider,
    @Inject private val timeZoneResourceMapper: TimeZoneResourceMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
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
            ApiResponse(
                responseCode = "400",
                description = FLOW_STATE_BAD_REQUEST_DESCRIPTION + FLOW_ONGOING_SESSION_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = FLOW_QUERY_STATE_UNAUTHORIZED_DESCRIPTION),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: `claimId` names no enabled claim of type `timezone`."
            )
        ],
        tags = ["flow"]
    )
    suspend fun listTimeZones(
        authentication: Authentication,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
        @PathVariable @Parameter(description = "Identifier of the claim.") claimId: String
    ): List<TimeZoneResource> = interactiveAuthFlowSessionControllerUtil.fetchOnGoingSessionThenRun(
        state = authentication.stateOrNull,
        observedRequest = observedRequest
    ) { _, _ ->
        val claim = claimManager.findByIdOrNull(claimId)
            ?.takeIf { it.dataType == ClaimDataType.TIMEZONE }
            .orNotFound()
        val timeZones = timeZoneProvider.listAvailableTimezones(claim)
        timeZoneResourceMapper.toResources(timeZones)
    }
}
