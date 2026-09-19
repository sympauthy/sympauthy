package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminUserClaimListResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.collection.UserClaimCollectionManager
import com.sympauthy.business.manager.user.UserManager
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

@Controller("/api/v1/admin/users/{userId}/claims")
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
class AdminUserClaimController(
    @Inject private val userManager: UserManager,
    @Inject private val userClaimCollectionManager: UserClaimCollectionManager,
    @Inject private val userClaimMapper: AdminUserClaimResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve a paginated list of claims for a given user, with metadata. " +
                "Claims are ordered by identifier unless another order is asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "/api/v1/admin/users/{userId}/claims/capabilities.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of user claims."),
            ApiResponse(
                responseCode = "400",
                description = "Invalid page or size, an unknown field, an operator the field does not " +
                        "accept, or a value it does not hold."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:read."
            ),
            ApiResponse(responseCode = "404", description = "No user found with the given identifier.")
        ]
    )
    @Get
    @Secured(ADMIN_USERS_READ)
    suspend fun listUserClaims(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
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
    ): AdminUserClaimListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, userClaimCollectionManager.capabilities(), sort, q)
        userManager.findByIdOrNull(userId).orNotFound()

        val claims = userClaimCollectionManager.listUserClaims(userId, criteria, pageParams)

        return AdminUserClaimListResource(
            claims = claims.items.map(userClaimMapper::toResource),
            page = claims.page,
            size = claims.size,
            total = claims.total
        )
    }

    @Operation(
        description = "Retrieve what the user claim collection accepts: the fields it filters on and the " +
                "operators each admits, the fields it orders on, the fields a free-text search matches " +
                "against, and the order it takes when none is asked for. " +
                "It describes the collection rather than one account, so it reads the same for every user " +
                "identifier and looks none of them up. " +
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
    @Secured(ADMIN_USERS_READ)
    suspend fun getUserClaimCapabilities(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID
    ): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(userClaimCollectionManager.capabilities(), request.locale.orDefault())
}
