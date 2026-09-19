package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserDetailResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminUserDetailResource
import com.sympauthy.api.resource.admin.AdminUserListResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.user.CollectedClaimManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.manager.collection.UserCollectionManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import com.sympauthy.util.orDefault
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
    @Inject private val userCollectionManager: UserCollectionManager,
    @Inject private val collectedClaimManager: CollectedClaimManager,
    @Inject private val userMapper: AdminUserResourceMapper,
    @Inject private val userDetailMapper: AdminUserDetailResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    companion object {
        /**
         * The one parameter of this collection that is not a criterion: it selects which claim values
         * come back rather than which users do.
         */
        private val SELECTION_PARAMS = setOf("claims")
    }

    @Operation(
        description = "Retrieve a paginated list of users. Claim values can be included in the response " +
                "by specifying the 'claims' parameter. " +
                "Every claim this deployment collects is a field of its own, named by the claim identifier, " +
                "so a filter on one is written the way every other filter is. " +
                "Users are ordered by creation date, oldest first, then by user identifier, unless another " +
                "order is asked for; that identifier stays ascending whichever direction is asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "/api/v1/admin/users/capabilities.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of users."),
            ApiResponse(
                responseCode = "400",
                description = "Invalid page, size or claim ID, an unknown field, an operator the field " +
                        "does not accept, or a value it does not hold."
            ),
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
        @QueryValue @Parameter(
            description = "Comma-separated list of claim IDs to include in the response. " +
                    "Absent: all enabled claims. Empty string: no claims. Example: email,name."
        ) claims: String?,
        @QueryValue @Parameter(
            description = "Comma-separated list of keys to order by, each prefixed with - to read it " +
                    "from the largest value to the smallest. The keys are published by the capabilities " +
                    "endpoint."
        ) sort: String?,
        @QueryValue @Parameter(
            description = "Partial case-insensitive search across the fields the capabilities endpoint " +
                    "publishes as searchable."
        ) q: String?
    ): AdminUserListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, userCollectionManager.capabilities(), sort, q, SELECTION_PARAMS)
        val selectedClaims = userCollectionManager.listSelectedClaims(claimIdsOf(claims))
        val users = userCollectionManager.listUsers(criteria, pageParams)

        return AdminUserListResource(
            users = users.items.map { userMapper.toResource(it, selectedClaims) },
            page = users.page,
            size = users.size,
            total = users.total
        )
    }

    @Operation(
        description = "Retrieve what the user collection accepts: the fields it filters on and the " +
                "operators each admits, the fields it orders on, the fields a free-text search matches " +
                "against, and the order it takes when none is asked for. " +
                "Every claim this deployment collects appears as a field of its own, which is what lets a " +
                "console render a filter on a claim it was never built knowing about. " +
                "The names it carries are read in the language the request asked for and may be reworded " +
                "in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection accepts."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:read."
            )
        ]
    )
    @Get("/capabilities")
    suspend fun getUserCapabilities(request: HttpRequest<*>): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(userCollectionManager.capabilities(), request.locale.orDefault())

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
