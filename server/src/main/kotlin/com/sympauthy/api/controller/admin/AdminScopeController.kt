package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminScopeResourceMapper
import com.sympauthy.api.resource.admin.AdminScopeListResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.filterOf
import com.sympauthy.business.manager.ScopeSearchManager
import com.sympauthy.business.model.oauth2.*
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

@Controller("/api/v1/admin/scopes")
@Secured(ADMIN_CONFIG_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONFIG_READ])
class AdminScopeController(
    @Inject private val scopeSearchManager: ScopeSearchManager,
    @Inject private val scopeMapper: AdminScopeResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve all the scopes this server knows about (consentable, grantable, client), " +
                "including the ones this deployment turned off. Since scopes are defined in configuration, " +
                "this endpoint exposes them as read-only resources. Scopes are ordered by scope.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of scopes."),
            ApiResponse(responseCode = "400", description = "Invalid page or size."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get
    suspend fun listScopes(
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?,
        @QueryValue @Parameter(
            description = "Filter by scope type: consentable, grantable or client. A value that is none " +
                    "of them is refused."
        ) type: String?,
        @QueryValue @Parameter(
            description = "Filter by whether this deployment serves the scope. Omit to list both."
        ) enabled: Boolean?
    ): AdminScopeListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val scopes = scopeSearchManager.listScopes(
            type = filterOf<ScopeType>("type", type),
            enabled = enabled,
            pageParams = pageParams
        )
        return AdminScopeListResource(
            scopes = scopes.items.map { scopeMapper.toResource(it.scope, it.protectedClaims) },
            page = scopes.page,
            size = scopes.size,
            total = scopes.total
        )
    }
}
