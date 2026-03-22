package com.sympauthy.api.controller.admin

import com.sympauthy.api.resource.admin.AdminUserProviderListResource
import com.sympauthy.api.resource.admin.AdminUserProviderResource
import com.sympauthy.api.resource.admin.AdminUserProviderUnlinkResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.provider.ProviderClaimsManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import com.sympauthy.security.SecurityRule.ADMIN_USERS_WRITE
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users/{userId}/providers")
class AdminUserProviderController(
    @Inject private val userManager: UserManager,
    @Inject private val providerClaimsManager: ProviderClaimsManager
) {

    @Operation(
        description = "Retrieve a paginated list of external identity providers linked to a user.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "userId",
                description = "Unique identifier of the user.",
                schema = Schema(type = "string", format = "uuid")
            ),
            Parameter(
                name = "page",
                description = "Zero-indexed page number.",
                schema = Schema(type = "integer", defaultValue = "0")
            ),
            Parameter(
                name = "size",
                description = "Number of results per page.",
                schema = Schema(type = "integer", defaultValue = "20")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of linked providers."),
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
    suspend fun listProviders(
        @PathVariable userId: UUID,
        @QueryValue page: Int?,
        @QueryValue size: Int?
    ): AdminUserProviderListResource {
        userManager.findByIdOrNull(userId).orNotFound()
        val (page, size) = resolvePageParams(page, size)
        val allProviders = providerClaimsManager.findByUserId(userId)
        val paged = allProviders
            .drop(page * size)
            .take(size)
            .map {
                AdminUserProviderResource(
                    providerId = it.providerId,
                    subject = it.userInfo.subject,
                    linkedAt = it.fetchDate
                )
            }
        return AdminUserProviderListResource(
            providers = paged,
            page = page,
            size = size,
            total = allProviders.size
        )
    }

    @Operation(
        description = "Remove the link between a user and an external identity provider.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "userId",
                description = "Unique identifier of the user.",
                schema = Schema(type = "string", format = "uuid")
            ),
            Parameter(
                name = "providerId",
                description = "Identifier of the provider to unlink.",
                schema = Schema(type = "string")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Provider unlinked successfully."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:write."
            ),
            ApiResponse(
                responseCode = "404",
                description = "No user found with the given identifier, or no provider link found."
            )
        ]
    )
    @Delete("/{providerId}")
    @Secured(ADMIN_USERS_WRITE)
    suspend fun unlinkProvider(
        @PathVariable userId: UUID,
        @PathVariable providerId: String
    ): AdminUserProviderUnlinkResource {
        userManager.findByIdOrNull(userId).orNotFound()
        providerClaimsManager.findByUserIdAndProviderIdOrNull(userId, providerId).orNotFound()
        providerClaimsManager.deleteProviderLink(userId, providerId)
        return AdminUserProviderUnlinkResource(
            userId = userId,
            providerId = providerId,
            unlinked = true
        )
    }
}
