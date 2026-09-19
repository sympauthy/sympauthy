package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminInvitationResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminCreateInvitationInputResource
import com.sympauthy.api.resource.admin.AdminCreatedInvitationResource
import com.sympauthy.api.resource.admin.AdminInvitationListResource
import com.sympauthy.api.resource.admin.AdminInvitationResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.collection.InvitationCollectionManager
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_INVITATIONS_READ
import com.sympauthy.security.SecurityRule.ADMIN_INVITATIONS_WRITE
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.time.LocalDateTime
import java.util.*

@Controller("/api/v1/admin/invitations")
class AdminInvitationController(
    @Inject private val invitationManager: InvitationManager,
    @Inject private val invitationCollectionManager: InvitationCollectionManager,
    @Inject private val invitationMapper: AdminInvitationResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description =
            "Create a new invitation for the given audience. The raw token is returned only in this response.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "201", description = "Invitation created."),
            ApiResponse(
                responseCode = "400",
                description = "A pre-assigned claim was refused. Only claims the named audience has " +
                        "may be pre-assigned."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:invitations:write."
            )
        ]
    )
    @Post
    @Status(HttpStatus.CREATED)
    @Secured(ADMIN_INVITATIONS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_WRITE])
    suspend fun createInvitation(
        @Body input: AdminCreateInvitationInputResource
    ): AdminCreatedInvitationResource {
        val expiresAt = input.expiresAt?.let { LocalDateTime.parse(it) }
        val (invitation, rawToken) = invitationManager.createInvitation(
            audienceId = input.audienceId,
            claims = input.claims,
            note = input.note,
            expiresAt = expiresAt,
            createdBy = InvitationCreatedBy.ADMIN,
        )
        return invitationMapper.toCreatedResource(invitation, rawToken)
    }

    @Operation(
        description = "Retrieve a paginated list of invitations. Invitations are ordered by creation " +
                "date, oldest first, then by identifier, unless another order is asked for. " +
                "Which fields this collection can be filtered, ordered and row on is published at " +
                "/api/v1/admin/invitations/capabilities.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of invitations."),
            ApiResponse(
                responseCode = "400",
                description = "Invalid page or size, an unknown field, an operator the field does not " +
                        "accept, or a value it does not hold."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:invitations:read."
            )
        ]
    )
    @Get
    @Secured(ADMIN_INVITATIONS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_READ])
    suspend fun listInvitations(
        request: HttpRequest<*>,
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
    ): AdminInvitationListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, invitationCollectionManager.capabilities(), sort, q)
        val invitations = invitationCollectionManager.listInvitations(criteria, pageParams)
        return AdminInvitationListResource(
            invitations = invitations.items.map(invitationMapper::toResource),
            page = invitations.page,
            size = invitations.size,
            total = invitations.total
        )
    }

    @Operation(
        description = "Retrieve what the invitation collection accepts: the fields it filters on and the " +
                "operators each admits, the fields it orders on, the fields a free-text search matches " +
                "against, and the order it takes when none is asked for. " +
                "The names it carries are read in the language the request asked for and may be reworded " +
                "in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection accepts."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:invitations:read."
            )
        ]
    )
    @Get("/capabilities")
    @Secured(ADMIN_INVITATIONS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_READ])
    suspend fun getInvitationCapabilities(request: HttpRequest<*>): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(invitationCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Retrieve a single invitation by its identifier.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Invitation details."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:invitations:read."
            ),
            ApiResponse(responseCode = "404", description = "Invitation not found.")
        ]
    )
    @Get("/{invitationId}")
    @Secured(ADMIN_INVITATIONS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_READ])
    suspend fun getInvitation(
        @PathVariable @Parameter(description = "Unique identifier of the invitation.") invitationId: UUID
    ): AdminInvitationResource {
        val invitation = invitationManager.findByIdOrNull(invitationId).orNotFound()
        return invitationMapper.toResource(invitation)
    }

    @Operation(
        description = "Revoke a pending invitation.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Invitation revoked."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:invitations:write."
            ),
            ApiResponse(responseCode = "404", description = "Invitation not found.")
        ]
    )
    @Post("/{invitationId}/revoke")
    @Secured(ADMIN_INVITATIONS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_WRITE])
    suspend fun revokeInvitation(
        @PathVariable @Parameter(description = "Unique identifier of the invitation to revoke.") invitationId: UUID
    ): AdminInvitationResource {
        val invitation = invitationManager.revokeInvitation(invitationId)
        return invitationMapper.toResource(invitation)
    }
}
