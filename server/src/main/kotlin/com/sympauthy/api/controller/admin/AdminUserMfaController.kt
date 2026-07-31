package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.mapper.admin.AdminUserMfaMethodResourceMapper
import com.sympauthy.api.resource.admin.AdminUserMfaMethodListResource
import com.sympauthy.api.resource.admin.AdminUserMfaRevokeResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import com.sympauthy.security.SecurityRule.ADMIN_USERS_WRITE
import io.micronaut.http.HttpStatus.NOT_FOUND
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users/{userId}/mfa")
class AdminUserMfaController(
    @Inject private val userManager: UserManager,
    @Inject private val totpManager: TotpManager,
    @Inject private val mfaMapper: AdminUserMfaMethodResourceMapper
) {

    @Operation(
        description = "Retrieve a paginated list of registered MFA methods for a given user.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of MFA methods."),
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
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
    suspend fun listMfaMethods(
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(description = "Number of results per page.") size: Int?
    ): AdminUserMfaMethodListResource {
        userManager.findByIdOrNull(userId).orNotFound()
        val (page, size) = resolvePageParams(page, size)
        val allEnrollments = totpManager.findConfirmedEnrollments(userId)
        val paged = allEnrollments
            .drop(page * size)
            .take(size)
            .map(mfaMapper::toResource)
        return AdminUserMfaMethodListResource(
            mfaMethods = paged,
            page = page,
            size = size,
            total = allEnrollments.size
        )
    }

    @Operation(
        description = "Revoke a specific MFA method registered by a user. " +
                "The user will need to re-enroll on their next sign-in if MFA is required.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "MFA method revoked successfully."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:write."
            ),
            ApiResponse(responseCode = "404", description = "No MFA registration found with the given identifier.")
        ]
    )
    @Delete("/{mfaId}")
    @Secured(ADMIN_USERS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_WRITE])
    suspend fun revokeMfaMethod(
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @PathVariable @Parameter(description = "Unique identifier of the MFA registration to revoke.") mfaId: UUID
    ): AdminUserMfaRevokeResource {
        userManager.findByIdOrNull(userId).orNotFound()
        val enrollment = totpManager.findConfirmedEnrollmentOrNull(mfaId).orNotFound()
        if (enrollment.userId != userId) {
            throw httpExceptionOf(NOT_FOUND, "not_found", "description.not_found")
        }
        totpManager.deleteEnrollment(enrollment)
        return AdminUserMfaRevokeResource(
            userId = userId,
            mfaId = mfaId,
            revoked = true
        )
    }
}
