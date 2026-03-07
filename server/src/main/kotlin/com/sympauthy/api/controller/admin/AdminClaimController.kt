package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminClaimListResource
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.ClaimManager
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.security.SecurityRule.ADMIN_CONFIG_READ
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Controller("/api/v1/admin/claims")
@Secured(ADMIN_CONFIG_READ)
class AdminClaimController(
    @Inject private val claimManager: ClaimManager,
    @Inject private val claimMapper: AdminClaimResourceMapper
) {

    @Operation(
        description = "Retrieve all configured claims (standard and custom). Since claims are defined in configuration files, this endpoint exposes them as read-only resources.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "page",
                description = "Zero-indexed page number.",
                schema = Schema(type = "integer", defaultValue = "0")
            ),
            Parameter(
                name = "size",
                description = "Number of results per page.",
                schema = Schema(type = "integer", defaultValue = "20")
            ),
            Parameter(
                name = "enabled",
                description = "Filter by enabled status.",
                schema = Schema(type = "boolean")
            ),
            Parameter(
                name = "required",
                description = "Filter by required status.",
                schema = Schema(type = "boolean")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of claims."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get
    fun listClaims(
        @QueryValue page: Int?,
        @QueryValue size: Int?,
        @QueryValue enabled: Boolean?,
        @QueryValue required: Boolean?
    ): AdminClaimListResource {
        val (page, size) = resolvePageParams(page, size)
        val claims = claimManager.listAllClaims()
            .let { list -> if (enabled != null) list.filter { it.enabled == enabled } else list }
            .let { list -> if (required != null) list.filter { it.required == required } else list }
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
