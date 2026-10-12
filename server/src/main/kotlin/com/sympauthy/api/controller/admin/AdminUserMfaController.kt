package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.flow.InteractiveFlowStepUriMapper
import com.sympauthy.api.controller.flow.auth.InteractiveAuthFlowSessionControllerUtil
import com.sympauthy.api.exception.httpExceptionOf
import com.sympauthy.api.filter.ObservedRequestFilter.Companion.OBSERVED_REQUEST
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminUserMfaMethodResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminUserMfaEnrollmentInputResource
import com.sympauthy.api.resource.admin.AdminUserMfaEnrollmentResource
import com.sympauthy.api.resource.admin.AdminUserMfaMethodListResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.COLLECTION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.COLLECTION_SEARCH_UNSUPPORTED_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_NAME
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.RETURN_URI_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SORT_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.exception.recoverableBusinessExceptionOf
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.client.ClientRedirectUriManager
import com.sympauthy.business.manager.flow.InteractiveFlowEngine
import com.sympauthy.business.manager.flow.auth.InteractiveAuthFlowSessionManager
import com.sympauthy.business.manager.flow.mfa.InteractiveFlowSessionMfaEnrollmentManager
import com.sympauthy.business.manager.collection.MfaEnrollmentCollectionManager
import com.sympauthy.business.manager.mfa.TotpManager
import com.sympauthy.business.manager.user.UserManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.business.model.security.ObservedRequest
import com.sympauthy.config.model.EnabledMfaConfig
import com.sympauthy.config.model.MfaConfig
import com.sympauthy.security.SecurityRule.ADMIN_USERS_READ
import com.sympauthy.security.SecurityRule.ADMIN_USERS_WRITE
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpStatus.NOT_FOUND
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.Explode
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.enums.ParameterStyle
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/users/{userId}/mfa")
class AdminUserMfaController(
    @Inject private val userManager: UserManager,
    @Inject private val totpManager: TotpManager,
    @Inject private val mfaEnrollmentCollectionManager: MfaEnrollmentCollectionManager,
    @Inject private val mfaMapper: AdminUserMfaMethodResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val interactiveAuthFlowSessionManager: InteractiveAuthFlowSessionManager,
    @Inject private val clientRedirectUriManager: ClientRedirectUriManager,
    @Inject private val clientManager: ClientManager,
    @Inject private val mfaEnrollmentManager: InteractiveFlowSessionMfaEnrollmentManager,
    @Inject private val engine: InteractiveFlowEngine,
    @Inject private val stepUriMapper: InteractiveFlowStepUriMapper,
    @Inject private val interactiveAuthFlowSessionControllerUtil: InteractiveAuthFlowSessionControllerUtil,
    @Inject private val uncheckedMfaConfig: MfaConfig,
    @Inject private val paginationUtil: PaginationUtil,
) {

    @Operation(
        description = "Retrieve the MFA methods enrolled for a user. A method the end-user has not " +
                "confirmed yet is not listed. " +
                "MFA methods are ordered by the date they were confirmed, oldest first, unless another " +
                "order is asked for. " +
                "Which fields this collection can be filtered and ordered on is published at " +
                "/api/v1/admin/users/{userId}/mfa/capabilities.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = FILTER_PARAMETER_NAME,
                `in` = ParameterIn.QUERY,
                description = FILTER_PARAMETER_DESCRIPTION,
                style = ParameterStyle.FORM,
                explode = Explode.TRUE,
                schema = Schema(
                    type = "object",
                    additionalProperties = Schema.AdditionalPropertiesValue.USE_ADDITIONAL_PROPERTIES_ANNOTATION,
                    additionalPropertiesSchema = String::class
                )
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of MFA methods."),
            ApiResponse(
                responseCode = "400",
                description = COLLECTION_BAD_REQUEST_DESCRIPTION + COLLECTION_SEARCH_UNSUPPORTED_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:read`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: no user has this identifier."
            )
        ]
    )
    @Get
    @Secured(ADMIN_USERS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
    suspend fun listMfaMethods(
        request: HttpRequest<*>,
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?
    ): AdminUserMfaMethodListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, mfaEnrollmentCollectionManager.capabilities(), sort)
        userManager.findByIdOrNull(userId).orNotFound()
        val enrollments = mfaEnrollmentCollectionManager.listConfirmedEnrollments(userId, criteria, pageParams)
        return AdminUserMfaMethodListResource(
            mfaMethods = enrollments.items.map(mfaMapper::toResource),
            page = enrollments.page,
            size = enrollments.size,
            total = enrollments.total
        )
    }

    @Operation(
        description = "Retrieve what the collection of a user's MFA methods accepts: the fields it filters " +
                "on and the operators each accepts, the fields it orders on, and its own order. It searches " +
                "nothing. " +
                "Each `name` is in the language the request asks for, and may be reworded in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection of a user's MFA methods accepts."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:read`."
            )
        ]
    )
    @Get("/capabilities")
    @Secured(ADMIN_USERS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_READ])
    suspend fun getMfaCapabilities(
        request: HttpRequest<*>,
        @PathVariable @Parameter(
            description = "Identifier of a user. It is not looked up: the answer is the same for every user."
        ) userId: UUID
    ): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(mfaEnrollmentCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Revoke an MFA method of a user. Where the configuration requires MFA, an end-user " +
                "left with no MFA method enrolls a new one the next time they sign in.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "204", description = "The MFA method was revoked."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:write`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: no user has the identifier `userId`, or the user has no confirmed " +
                        "MFA method with the identifier `mfaId`."
            )
        ]
    )
    @Delete("/{mfaId}")
    @Secured(ADMIN_USERS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_WRITE])
    @Status(HttpStatus.NO_CONTENT)
    suspend fun revokeMfaMethod(
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID,
        @PathVariable @Parameter(description = "Identifier of the MFA method to revoke.") mfaId: UUID
    ) {
        userManager.findByIdOrNull(userId).orNotFound()
        val enrollment = totpManager.findConfirmedEnrollmentOrNull(mfaId).orNotFound()
        if (enrollment.userId != userId) {
            throw httpExceptionOf(NOT_FOUND, "not_found", "description.not_found")
        }
        totpManager.deleteEnrollment(enrollment)
    }

    @Operation(
        description = "Start enrolling an MFA method for a user. Send the end-user's browser to " +
                "`redirect_url`: the end-user confirms a request made by an administrator, enrolls the MFA " +
                "method, and is sent back to `return_uri`.",
        tags = ["admin"],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Where to send the end-user's browser to enroll an MFA method.",
                useReturnTypeSchema = true
            ),
            ApiResponse(
                responseCode = "400",
                description = "- `admin.users.mfa.enrollment.mfa_disabled`: `mfa.totp.enabled` is not `true` in " +
                        "the configuration.\n" + RETURN_URI_BAD_REQUEST_DESCRIPTION + "\n" +
                        PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION + "\n" +
                        BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:users:write`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: no user has the identifier `userId`, or `client_id` is absent or " +
                        "names no client."
            ),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ]
    )
    @Post("/enrollment")
    @Secured(ADMIN_USERS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.USERS_WRITE])
    suspend fun startEnrollment(
        @PathVariable @Parameter(description = "Identifier of the user.") userId: UUID,
        @RequestAttribute(OBSERVED_REQUEST) observedRequest: ObservedRequest,
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
        interactiveAuthFlowSessionControllerUtil.observeStartedSession(session, observedRequest)

        // The resulting redirect URI already carries the signed state as a query parameter.
        val (steppedSession, step) = engine.advance(session)
        val redirectUri = stepUriMapper.toRedirectUri(steppedSession, flow, step)
        return AdminUserMfaEnrollmentResource(
            redirectUrl = redirectUri.toString()
        )
    }
}
