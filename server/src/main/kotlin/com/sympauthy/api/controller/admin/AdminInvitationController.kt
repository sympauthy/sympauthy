package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminInvitationResourceMapper
import com.sympauthy.api.resource.admin.AdminCreateInvitationInputResource
import com.sympauthy.api.resource.admin.AdminCreatedInvitationResource
import com.sympauthy.api.resource.admin.AdminInvitationListResource
import com.sympauthy.api.resource.admin.AdminInvitationResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_INVITATIONS_READ
import com.sympauthy.security.SecurityRule.ADMIN_INVITATIONS_WRITE
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.time.LocalDateTime
import java.util.*

@Controller("/api/v1/admin/invitations")
class AdminInvitationController(
    @Inject private val invitationManager: InvitationManager,
    @Inject private val invitationMapper: AdminInvitationResourceMapper
) {

    @Operation(
        description = "Create a new invitation for the given audience. The raw token is returned only in this response.",
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
            audienceId = input.audience,
            claims = input.claims,
            note = input.note,
            expiresAt = expiresAt,
            createdBy = InvitationCreatedBy.ADMIN,
        )
        return invitationMapper.toCreatedResource(invitation, rawToken)
    }

    @Operation(
        description = "Retrieve a paginated list of invitations, optionally filtered by audience.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "audience_id",
                description = "Filter by audience identifier.",
                schema = Schema(type = "string")
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
            ApiResponse(responseCode = "200", description = "Paginated list of invitations."),
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
        @QueryValue("audience_id") audienceId: String?,
        @QueryValue page: Int?,
        @QueryValue size: Int?
    ): AdminInvitationListResource {
        val (resolvedPage, resolvedSize) = resolvePageParams(page, size)
        val allInvitations = if (audienceId != null) {
            invitationManager.findByAudienceId(audienceId)
        } else {
            invitationManager.findAll()
        }
        val paged = allInvitations
            .drop(resolvedPage * resolvedSize)
            .take(resolvedSize)
            .map(invitationMapper::toResource)
        return AdminInvitationListResource(
            invitations = paged,
            page = resolvedPage,
            size = resolvedSize,
            total = allInvitations.size
        )
    }

    @Operation(
        description = "Retrieve a single invitation by its identifier.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "invitationId",
                description = "Unique identifier of the invitation.",
                schema = Schema(type = "string", format = "uuid")
            )
        ],
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
        @PathVariable invitationId: UUID
    ): AdminInvitationResource {
        val invitation = invitationManager.findByIdOrNull(invitationId).orNotFound()
        return invitationMapper.toResource(invitation)
    }

    @Operation(
        description = "Revoke a pending invitation.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "invitationId",
                description = "Unique identifier of the invitation to revoke.",
                schema = Schema(type = "string", format = "uuid")
            )
        ],
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
        @PathVariable invitationId: UUID
    ): AdminInvitationResource {
        val invitation = invitationManager.revokeInvitation(invitationId)
        return invitationMapper.toResource(invitation)
    }
}
