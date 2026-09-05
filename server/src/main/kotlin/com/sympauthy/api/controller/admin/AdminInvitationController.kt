package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminInvitationResourceMapper
import com.sympauthy.api.resource.admin.AdminCreateInvitationInputResource
import com.sympauthy.api.resource.admin.AdminCreatedInvitationResource
import com.sympauthy.api.resource.admin.AdminInvitationListResource
import com.sympauthy.api.resource.admin.AdminInvitationResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.filterOf
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.invitation.InvitationSearchManager
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.invitation.InvitationStatus
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_INVITATIONS_READ
import com.sympauthy.security.SecurityRule.ADMIN_INVITATIONS_WRITE
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
    @Inject private val invitationSearchManager: InvitationSearchManager,
    @Inject private val invitationMapper: AdminInvitationResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description =
            "Create a new invitation for the given audience. The raw token is returned only in this response.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "201", description = "Invitation created."),
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
        description = "Retrieve a paginated list of invitations, optionally filtered by audience. " +
                "Invitations are ordered by creation date, oldest first, then by identifier.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of invitations."),
            ApiResponse(responseCode = "400", description = "Invalid page or size."),
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
        @QueryValue("audience_id") @Parameter(description = "Filter by audience identifier.") audienceId: String?,
        @QueryValue @Parameter(description = "Filter by invitation status.") status: String?,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?
    ): AdminInvitationListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val invitations = invitationSearchManager.listInvitations(
            audienceId = audienceId,
            status = filterOf<InvitationStatus>("status", status),
            pageParams = pageParams
        )
        return AdminInvitationListResource(
            invitations = invitations.items.map(invitationMapper::toResource),
            page = invitations.page,
            size = invitations.size,
            total = invitations.total
        )
    }

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
