package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminScopeResourceMapper
import com.sympauthy.api.resource.admin.AdminScopeListResource
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.ScopeManager
import com.sympauthy.business.model.oauth2.*
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

@Controller("/api/v1/admin/scopes")
@Secured(ADMIN_CONFIG_READ)
class AdminScopeController(
    @Inject private val scopeManager: ScopeManager,
    @Inject private val scopeMapper: AdminScopeResourceMapper
) {

    @Operation(
        description = "Retrieve all configured scopes (consentable, grantable, client). Since scopes are defined in configuration, this endpoint exposes them as read-only resources.",
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
                name = "type",
                description = "Filter by scope type.",
                schema = Schema(type = "string", allowableValues = ["consentable", "grantable", "client"])
            ),
            Parameter(
                name = "enabled",
                description = "Filter by enabled status.",
                schema = Schema(type = "boolean")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of scopes."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get
    suspend fun listScopes(
        @QueryValue page: Int?,
        @QueryValue size: Int?,
        @QueryValue type: String?,
        @QueryValue enabled: Boolean?
    ): AdminScopeListResource {
        val (page, size) = resolvePageParams(page, size)
        val scopes = scopeManager.listScopes()
            .let { list -> filterByType(list, type) }
            .let { list -> if (enabled != null) list.filter { enabled } else list }
            .sortedBy { it.scope }
        val paged = scopes
            .drop(page * size)
            .take(size)
            .map { scope ->
                val claims = scopeManager.listClaimsProtectedByScope(scope)
                scopeMapper.toResource(scope, claims)
            }
        return AdminScopeListResource(
            scopes = paged,
            page = page,
            size = size,
            total = scopes.size
        )
    }

    private fun filterByType(scopes: List<Scope>, type: String?): List<Scope> {
        return when (type?.lowercase()) {
            "consentable" -> scopes.filterIsInstance<ConsentableUserScope>()
            "grantable" -> scopes.filterIsInstance<GrantableUserScope>()
            "client" -> scopes.filterIsInstance<ClientScope>()
            null -> scopes
            else -> emptyList()
        }
    }
}
