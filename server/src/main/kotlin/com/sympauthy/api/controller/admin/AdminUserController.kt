package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminUserDetailResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserResourceMapper
import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.api.resource.admin.AdminUserListResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.user.UserSearchManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users")
@Secured(ADMIN_USERS_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
class AdminUserController(
    @Inject private val userManager: UserManager,
    @Inject private val userSearchManager: UserSearchManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val userMapper: AdminUserResourceMapper,
    @Inject private val userDetailMapper: AdminUserDetailResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    companion object {
        private val RESERVED_PARAMS = setOf("page", "size", "status", "claims", "q", "sort", "order")
    }

    @Operation(
        description = "Retrieve a paginated list of users with optional filtering, search, and sorting. " +
                "Claim values can be included in the response by specifying the 'claims' parameter. " +
                "Dynamic query parameters matching claim identifiers are treated as exact-match filters. " +
                "Users are ordered by the requested sort property — creation date, oldest first, when none " +
                "is named — then by user identifier, which stays ascending under order=desc.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of users."),
            ApiResponse(responseCode = "400", description = "Invalid page, size, claim ID, status, or sort property."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:read."
            )
        ]
    )
    @Get
    suspend fun listUsers(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?,
        @QueryValue @Parameter(description = "Filter by user status (e.g. enabled, disabled).") status: String?,
        @QueryValue @Parameter(
            description = "Comma-separated list of claim IDs to include in the response. " +
                    "Absent: all enabled claims. Empty string: no claims. Example: email,name."
        ) claims: String?,
        @QueryValue @Parameter(
            description = "Partial case-insensitive text search across all enabled claim values."
        ) q: String?,
        @QueryValue @Parameter(description = "Property to sort by: created_at, status, or a claim identifier.") sort: String?,
        @QueryValue @Parameter(description = "Sort direction: asc or desc.") order: String?
    ): AdminUserListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val selectedClaims = userSearchManager.listSelectedClaims(claimIdsOf(claims))

        val claimFilters = request.parameters
            .asMap()
            .filterKeys { it !in RESERVED_PARAMS }
            .mapValues { (_, values) -> values.first() }

        val users = userSearchManager.listUsers(
            status = status,
            query = q,
            claimFilters = claimFilters,
            sort = sort,
            order = order,
            pageParams = pageParams
        )

        return AdminUserListResource(
            users = users.items.map { userMapper.toResource(it, selectedClaims) },
            page = users.page,
            size = users.size,
            total = users.total
        )
    }

    @Operation(
        description = "Retrieve details for a specific user by their identifier.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "User details."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:read."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Get("/{id}")
    suspend fun getUser(
        @PathVariable @Parameter(description = "Unique identifier of the user.") id: UUID
    ): AdminUserDetailResource {
        val user = userManager.findByIdOrNull(id).orNotFound()
        val identifierClaims = collectedClaimManager.findIdentifierByUserId(user.id)
        return userDetailMapper.toResource(user, identifierClaims)
    }

    /**
     * Split the comma-separated `claims` parameter, keeping a caller who named none apart from one
     * who named no claim at all: the first sends nothing and the second an empty value.
     */
    private fun claimIdsOf(claims: String?): List<String>? = claims
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
}
