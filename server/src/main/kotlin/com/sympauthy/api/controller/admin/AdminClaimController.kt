package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminClaimListResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.security.SecurityRule.ADMIN_CONFIG_READ
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject

@Controller("/api/v1/admin/claims")
@Secured(ADMIN_CONFIG_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONFIG_READ])
class AdminClaimController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val claimMapper: AdminClaimResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve all configured claims (standard and custom). Since claims are defined in configuration files, this endpoint exposes them as read-only resources.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of claims."),
            ApiResponse(responseCode = "400", description = "Invalid page or size."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get
    fun listClaims(
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?,
        @QueryValue @Parameter(description = "Filter by enabled status.") enabled: Boolean?,
        @QueryValue @Parameter(description = "Filter by required status.") required: Boolean?,
        @QueryValue @Parameter(description = "Filter by claim origin.") origin: String?
    ): AdminClaimListResource {
        val (page, size) = paginationUtil.resolvePageParams(page, size)
        val claims = claimManager.listAllClaims()
            .let { list -> if (enabled != null) list.filter { it.enabled == enabled } else list }
            .let { list -> if (required != null) list.filter { it.required == required } else list }
            .let { list -> if (origin != null) list.filter { it.origin.value == origin.lowercase() } else list }
            .sortedWith(compareByDescending<Claim> { it.enabled }.thenBy { it.id })
        val paged = claims
            .drop(page * size)
            .take(size)
            .map(claimMapper::toResource)
        return AdminClaimListResource(
            claims = paged,
            page = page,
            size = size,
            total = claims.size
        )
    }
}
