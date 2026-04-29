package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientInvitationResourceMapper
import com.sympauthy.api.resource.client.ClientCreateInvitationInputResource
import com.sympauthy.api.resource.client.ClientCreatedInvitationResource
import com.sympauthy.api.resource.client.ClientInvitationListResource
import com.sympauthy.api.resource.client.ClientInvitationResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import com.sympauthy.security.SecurityRule.CLIENT_INVITATIONS_READ
import com.sympauthy.security.SecurityRule.CLIENT_INVITATIONS_WRITE
import com.sympauthy.security.clientAuthentication
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.time.LocalDateTime
import java.util.*

@Controller("/api/v1/client/invitations")
class ClientInvitationController(
    @Inject private val clientManager: ClientManager,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val invitationMapper: ClientInvitationResourceMapper
) {

    @Operation(
        description = "Create a new invitation for the client's audience. The raw token is returned only in this response.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "201", description = "Invitation created."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: invitations:write."
            )
        ]
    )
    @Post
    @Status(HttpStatus.CREATED)
    @Secured(CLIENT_INVITATIONS_WRITE)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.INVITATIONS_WRITE])
    suspend fun createInvitation(
        authentication: Authentication,
        @Body input: ClientCreateInvitationInputResource
    ): ClientCreatedInvitationResource {
        val clientAuth = authentication.clientAuthentication
        val client = clientManager.findClientById(clientAuth.clientId)
        val expiresAt = input.expiresAt?.let { LocalDateTime.parse(it) }
        val (invitation, rawToken) = invitationManager.createInvitation(
            audienceId = client.audience.id,
            claims = input.claims,
            note = input.note,
            expiresAt = expiresAt,
            createdBy = InvitationCreatedBy.CLIENT,
            createdById = client.id,
        )
        return invitationMapper.toCreatedResource(invitation, rawToken)
    }

    @Operation(
        description = "Retrieve a paginated list of invitations created by this client.",
        tags = ["client"],
        parameters = [
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
                description = "The access token does not include the required scope: invitations:read."
            )
        ]
    )
    @Get
    @Secured(CLIENT_INVITATIONS_READ)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.INVITATIONS_READ])
    suspend fun listInvitations(
        authentication: Authentication,
        @QueryValue page: Int?,
        @QueryValue size: Int?
    ): ClientInvitationListResource {
        val clientAuth = authentication.clientAuthentication
        val (resolvedPage, resolvedSize) = resolvePageParams(page, size)
        val allInvitations = invitationManager.findByCreatedById(clientAuth.clientId)
        val paged = allInvitations
            .drop(resolvedPage * resolvedSize)
            .take(resolvedSize)
            .map(invitationMapper::toResource)
        return ClientInvitationListResource(
            invitations = paged,
            page = resolvedPage,
            size = resolvedSize,
            total = allInvitations.size
        )
    }

    @Operation(
        description = "Retrieve a single invitation created by this client.",
        tags = ["client"],
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
                description = "The access token does not include the required scope: invitations:read."
            ),
            ApiResponse(responseCode = "404", description = "Invitation not found or not owned by this client.")
        ]
    )
    @Get("/{invitationId}")
    @Secured(CLIENT_INVITATIONS_READ)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.INVITATIONS_READ])
    suspend fun getInvitation(
        authentication: Authentication,
        @PathVariable invitationId: UUID
    ): ClientInvitationResource {
        val clientAuth = authentication.clientAuthentication
        val invitation = invitationManager.findByIdOrNull(invitationId)
            ?.takeIf { it.createdById == clientAuth.clientId }
            .orNotFound()
        return invitationMapper.toResource(invitation)
    }

    @Operation(
        description = "Revoke a pending invitation created by this client.",
        tags = ["client"],
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
                description = "The access token does not include the required scope: invitations:write."
            ),
            ApiResponse(responseCode = "404", description = "Invitation not found or not owned by this client.")
        ]
    )
    @Post("/{invitationId}/revoke")
    @Secured(CLIENT_INVITATIONS_WRITE)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.INVITATIONS_WRITE])
    suspend fun revokeInvitation(
        authentication: Authentication,
        @PathVariable invitationId: UUID
    ): ClientInvitationResource {
        val clientAuth = authentication.clientAuthentication
        invitationManager.findByIdOrNull(invitationId)
            ?.takeIf { it.createdById == clientAuth.clientId }
            .orNotFound()
        val invitation = invitationManager.revokeInvitation(invitationId)
        return invitationMapper.toResource(invitation)
    }
}
