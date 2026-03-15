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
        summary = "List timezones",
        description = """
List all timezones that are allowed to be selected for the claim.

The list of timezones will depends on:
- the version of the TZ database integrated into the authorization server.
- the allowed value declared for the claim in the configuration.

> Note on pagination: The timezone database contains around 600 entries which convert to a maximum payload of around 5kb 
once compressed with Gzip. Therefore pagination will be more hurtful to the performance than getting all the list in one go. 
This is why it has been decided than this operation will not support pagination.
        """,
        tags = ["flow"]
    )
    suspend fun listTimeZones(
        @PathVariable claimId: String
    ): List<TimeZoneResource> {
        val claim = claimManager.findByIdOrNull(claimId).orNotFound()
        val timeZones = timeZoneProvider.listAvailableTimezones(claim)
        return timeZoneResourceMapper.toResources(timeZones)
    }
}
