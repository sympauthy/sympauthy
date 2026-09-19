package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminClaimResourceMapper
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.resource.admin.AdminClaimListResource
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.business.manager.collection.ClaimCollectionManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_CONFIG_READ
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
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
    @Inject private val claimCollectionManager: ClaimCollectionManager,
    @Inject private val claimMapper: AdminClaimResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve all configured claims (standard and custom). Since claims are defined in " +
                "configuration files, this endpoint exposes them as read-only resources. Claims are ordered " +
                "with the enabled ones first, then by identifier, unless another order is asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "/api/v1/admin/claims/capabilities.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of claims."),
            ApiResponse(
                responseCode = "400",
                description = "Invalid page or size, an unknown field, an operator the field does not " +
                        "accept, or a value it does not hold."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get
    suspend fun listClaims(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?,
        @QueryValue @Parameter(
            description = "Comma-separated list of keys to order by, each prefixed with - to read it " +
                    "from the largest value to the smallest. The keys are published by the capabilities " +
                    "endpoint."
        ) sort: String?,
        @QueryValue @Parameter(
            description = "Partial case-insensitive search across the fields the capabilities endpoint " +
                    "publishes as searchable."
        ) q: String?
    ): AdminClaimListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, claimCollectionManager.capabilities(), sort, q)
        val claims = claimCollectionManager.listClaims(criteria, pageParams)
        return AdminClaimListResource(
            claims = claims.items.map(claimMapper::toResource),
            page = claims.page,
            size = claims.size,
            total = claims.total
        )
    }

    @Operation(
        description = "Retrieve what the claim collection accepts: the fields it filters on and the " +
                "operators each admits, the fields it orders on, the fields a free-text search matches " +
                "against, and the order it takes when none is asked for. " +
                "The names it carries are read in the language the request asked for and may be reworded " +
                "in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection accepts."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get("/capabilities")
    suspend fun getClaimCapabilities(request: HttpRequest<*>): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(claimCollectionManager.capabilities(), request.locale.orDefault())
}
