package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminUserDetailResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserResourceMapper
import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.api.resource.admin.AdminUserListResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.ClaimManager
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
import io.swagger.v3.oas.annotations.media.Schema
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
    @Inject private val claimManager: ClaimManager,
    @Inject private val userMapper: AdminUserResourceMapper,
    @Inject private val userDetailMapper: AdminUserDetailResourceMapper
) {

    companion object {
        private val RESERVED_PARAMS = setOf("page", "size", "status", "claims", "q", "sort", "order")
    }

    @Operation(
        description = "Retrieve a paginated list of users with optional filtering, search, and sorting. " +
                "Claim values can be included in the response by specifying the 'claims' parameter. " +
                "Dynamic query parameters matching claim identifiers are treated as exact-match filters.",
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
                name = "status",
                description = "Filter by user status (e.g. enabled, disabled).",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "claims",
                description = "Comma-separated list of claim IDs to include in the response. " +
                        "Absent: all enabled claims. Empty string: no claims. Example: email,name.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "q",
                description = "Partial case-insensitive text search across all enabled claim values.",
                schema = Schema(type = "string")
            ),
            Parameter(
                name = "sort",
                description = "Property to sort by: created_at, status, or a claim identifier.",
                schema = Schema(type = "string", defaultValue = "created_at")
            ),
            Parameter(
                name = "order",
                description = "Sort direction: asc or desc.",
                schema = Schema(type = "string", defaultValue = "asc")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of users."),
            ApiResponse(responseCode = "400", description = "Invalid claim ID, status, or sort property."),
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
        @QueryValue page: Int?,
        @QueryValue size: Int?,
        @QueryValue status: String?,
        @QueryValue claims: String?,
        @QueryValue q: String?,
        @QueryValue sort: String?,
        @QueryValue order: String?
    ): AdminUserListResource {
        val (page, size) = resolvePageParams(page, size)

        // Resolve selected claims
        val selectedClaims = resolveSelectedClaims(claims)

        // Extract dynamic claim filters from query params
        val claimFilters = request.parameters
            .asMap()
            .filterKeys { it !in RESERVED_PARAMS }
            .mapValues { (_, values) -> values.first() }

        // Search, filter, sort
        val allUsers = userSearchManager.listUsers(
            status = status,
            query = q,
            claimFilters = claimFilters,
            sort = sort,
            order = order
        )

        // Paginate
        val paged = allUsers
            .drop(page * size)
            .take(size)
            .map { uwc ->
                val claimsMap = userMapper.buildClaimsMap(uwc.collectedClaims, selectedClaims)
                userMapper.toResource(uwc.user, claimsMap)
            }

        return AdminUserListResource(
            users = paged,
            page = page,
            size = size,
            total = allUsers.size
        )
    }

    @Operation(
        description = "Retrieve details for a specific user by their identifier.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "id",
                description = "Unique identifier of the user.",
                schema = Schema(type = "string", format = "uuid")
            )
        ],
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
        @PathVariable id: UUID
    ): AdminUserDetailResource {
        val user = userManager.findByIdOrNull(id).orNotFound()
        val identifierClaims = collectedClaimManager.findIdentifierByUserId(user.id)
        return userDetailMapper.toResource(user, identifierClaims)
    }

    /**
     * Resolve the 'claims' query parameter:
     * - absent (null) → all enabled claims
     * - empty string → null (omit claims from response)
     * - comma-separated → validate and return selected claims
     */
    private fun resolveSelectedClaims(claims: String?) = when {
        claims == null -> claimManager.listEnabledClaims()
        claims.isBlank() -> null
        else -> {
            val claimIds = claims.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            userSearchManager.validateAndResolveClaimIds(claimIds)
        }
    }
}
