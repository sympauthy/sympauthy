package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminUserClaimResourceMapper
import com.sympauthy.api.resource.admin.AdminUserClaimListResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.valueFilterOf
import com.sympauthy.business.manager.user.UserClaimSearchManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.user.claim.ClaimOrigin
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
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
    @Inject private val userClaimSearchManager: UserClaimSearchManager,
    @Inject private val userClaimMapper: AdminUserClaimResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve a paginated list of claims for a given user, with metadata and filtering. " +
                "Claims are ordered by identifier.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of user claims."),
            ApiResponse(responseCode = "400", description = "Invalid page or size."),
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
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?,
        @QueryValue("claim_id") @Parameter(description = "Filter by specific claim identifier.") claimId: String?,
        @QueryValue @Parameter(description = "Filter by whether the claim is an identifier claim.") identifier: Boolean?,
        @QueryValue @Parameter(description = "Filter by whether the claim is required.") required: Boolean?,
        @QueryValue @Parameter(description = "Filter by whether the claim has been collected.") collected: Boolean?,
        @QueryValue @Parameter(description = "Filter by whether the claim has been verified.") verified: Boolean?,
        @QueryValue @Parameter(description = "Filter by claim origin.") origin: String?
    ): AdminUserClaimListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        userManager.findByIdOrNull(userId).orNotFound()

        val claims = userClaimSearchManager.listUserClaims(
            userId = userId,
            claimId = claimId,
            identifier = identifier,
            required = required,
            collected = collected,
            verified = verified,
            origin = valueFilterOf<ClaimOrigin>(origin) { it.value },
            pageParams = pageParams
        )

        return AdminUserClaimListResource(
            claims = claims.items.map(userClaimMapper::toResource),
            page = claims.page,
            size = claims.size,
            total = claims.total
        )
    }
}
