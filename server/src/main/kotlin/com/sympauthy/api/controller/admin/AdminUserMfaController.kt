package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.mapper.admin.AdminUserMfaMethodResourceMapper
import com.sympauthy.api.resource.admin.AdminUserMfaEnrollmentInputResource
import com.sympauthy.api.resource.admin.AdminUserMfaEnrollmentResource
import com.sympauthy.api.resource.admin.AdminUserMfaMethodListResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.business.manager.mfa.MfaEnrollmentSearchManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import com.sympauthy.security.SecurityRule.ADMIN_USERS_WRITE
import io.micronaut.http.HttpStatus
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
    @Inject private val mfaEnrollmentSearchManager: MfaEnrollmentSearchManager,
    @Inject private val mfaMapper: AdminUserMfaMethodResourceMapper,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val clientRedirectUriManager: ClientRedirectUriManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val paginationUtil: PaginationUtil,
) {

    @Operation(
        description = "Retrieve a paginated list of registered MFA methods for a given user. Methods are " +
                "ordered by the date the user confirmed them, oldest first, then by identifier.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of MFA methods."),
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
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
    suspend fun listMfaMethods(
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?
    ): AdminUserMfaMethodListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        userManager.findByIdOrNull(userId).orNotFound()
        val enrollments = mfaEnrollmentSearchManager.listConfirmedEnrollments(userId, pageParams)
        return AdminUserMfaMethodListResource(
            mfaMethods = enrollments.items.map(mfaMapper::toResource),
            page = enrollments.page,
            size = enrollments.size,
            total = enrollments.total
        )
    }

    @Operation(
        description = "Revoke a specific MFA method registered by a user. " +
                "The user will need to re-enroll on their next sign-in if MFA is required.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "204", description = "MFA method revoked successfully."),
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
    @Status(HttpStatus.NO_CONTENT)
    suspend fun revokeMfaMethod(
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @PathVariable @Parameter(description = "Unique identifier of the MFA registration to revoke.") mfaId: UUID
    ) {
        userManager.findByIdOrNull(userId).orNotFound()
        val enrollment = totpManager.findConfirmedEnrollmentOrNull(mfaId).orNotFound()
        if (enrollment.userId != userId) {
            throw httpExceptionOf(NOT_FOUND, "not_found", "description.not_found")
        }
        totpManager.deleteEnrollment(enrollment)
    }

    @Operation(
        description = "Start an on-demand MFA enrollment for a given user, initiated by an administrator. " +
                "Validates that the user and the named client exist and that the return (and optional cancel) " +
                "URI are registered redirect URIs of that client, then creates an enrollment session gated by a " +
                "confirmation the end-user must approve (shown as initiated by an administrator). Returns the " +
                "redirect_url to hand or send to the user; once enrollment completes they are redirected to " +
                "return_uri.",
        tags = ["admin"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "The enrollment session was started.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid return or cancel URI, or MFA is not enabled on this server."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:users:write."
            ),
            ApiResponse(responseCode = "404", description = "No user or client found with the given identifier.")
        ]
    )
    @Post("/enrollment")
    @Secured(ADMIN_USERS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_WRITE])
    suspend fun startEnrollment(
        @PathVariable @Parameter(description = "Unique identifier of the user.") userId: UUID,
        @Body resource: AdminUserMfaEnrollmentInputResource
    ): AdminUserMfaEnrollmentResource {
        // Fail fast (before creating any session) when MFA is not enabled on this server.
        if ((uncheckedMfaConfig as? EnabledMfaConfig)?.enabled != true) {
            throw recoverableBusinessExceptionOf(
                "admin.users.mfa.enrollment.mfa_disabled",
                "description.admin.users.mfa.enrollment.mfa_disabled"
            )
        }

        // Both the target user and the named client must exist.
        userManager.findByIdOrNull(userId).orNotFound()
        val client = resource.clientId
            ?.let { clientManager.findClientByIdOrNull(it) }
            .orNotFound()

        // Validate the return URI (and the optional cancel URI) against the named client's registered
        // redirect URIs to avoid open redirects. recoverable = true: a bad URI is a bad request from the
        // calling administrator (400), not a server error.
        val returnUri = clientRedirectUriManager.parseRequestedRedirectUri(
            client,
            resource.returnUri,
            recoverable = true
        )
        val cancelUri = resource.cancelUri
            ?.let { clientRedirectUriManager.parseRequestedRedirectUri(client, it, recoverable = true) }

        // Admin-initiated: pass a null client id so the confirmation shows "an administrator".
        val flow = interactiveAuthFlowSessionManager.getDefaultInteractiveFlow()
        val session = mfaEnrollmentManager.startMfaEnrollmentSession(
            userId = userId,
            returnUri = returnUri,
            flow = flow,
            initiatingClientId = null,
            cancelUri = cancelUri
        )

        // The resulting redirect URI already carries the signed state as a query parameter.
        val (steppedSession, step) = engine.advance(session)
        val redirectUri = stepUriMapper.toRedirectUri(steppedSession, flow, step)
        return AdminUserMfaEnrollmentResource(
            redirectUrl = redirectUri.toString()
        )
    }
}
