package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientInvitationResourceMapper
import com.sympauthy.api.resource.client.ClientCreateInvitationInputResource
import com.sympauthy.api.resource.client.ClientCreatedInvitationResource
import com.sympauthy.api.resource.client.ClientInvitationListResource
import com.sympauthy.api.resource.client.ClientInvitationResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.manager.invitation.InvitationManager
import com.sympauthy.business.manager.collection.InvitationCollectionManager
import com.sympauthy.business.model.invitation.InvitationCreatedBy
import com.sympauthy.business.model.oauth2.BuiltInClientScopeId
import com.sympauthy.security.SecurityRule.CLIENT_INVITATIONS_READ
import com.sympauthy.security.SecurityRule.CLIENT_INVITATIONS_WRITE
import com.sympauthy.security.clientAuthentication
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.time.LocalDateTime
import java.util.*

@Controller("/api/v1/client/invitations")
class ClientInvitationController(
    @Inject private val clientManager: ClientManager,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val invitationCollectionManager: InvitationCollectionManager,
    @Inject private val invitationMapper: ClientInvitationResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description =
            "Create a new invitation for the client's audience. The raw token is returned only in this response.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "201", description = "Invitation created."),
            ApiResponse(
                responseCode = "400",
                description = "A pre-assigned claim was refused. The client may pre-assign only its " +
                        "own audience's claims, and only those it may write."
            ),
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
        val clientScopeIds = clientAuth.scopes.map { it.scope }
        val (invitation, rawToken) = invitationManager.createInvitation(
            audienceId = client.audience.id,
            claims = input.claims,
            note = input.note,
            expiresAt = expiresAt,
            createdBy = InvitationCreatedBy.CLIENT,
            createdById = client.id,
            clientScopeIds = clientScopeIds,
        )
        return invitationMapper.toCreatedResource(invitation, rawToken)
    }

    @Operation(
        description = "Retrieve a paginated list of invitations created by this client. Invitations are " +
                "ordered by creation date, oldest first, then by identifier, unless another order is " +
                "asked for. " +
                "They can be filtered on status, note, token_prefix, created_at, expires_at, consumed_at, " +
                "revoked_at, consumed_by_user_id and id, and row with q across the note and the " +
                "token prefix. An operator other than an exact match is written as a dotted suffix on the " +
                "field name: status.in, created_at.gte, note.contains.",
        tags = ["client"],
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
                description = "The access token does not include the required scope: invitations:read."
            )
        ]
    )
    @Get
    @Secured(CLIENT_INVITATIONS_READ)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.INVITATIONS_READ])
    suspend fun listInvitations(
        request: HttpRequest<*>,
        authentication: Authentication,
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?,
        @QueryValue @Parameter(
            description = "Comma-separated list of keys to order by, each prefixed with - to read it " +
                    "from the largest value to the smallest."
        ) sort: String?,
        @QueryValue @Parameter(
            description = "Partial case-insensitive search across the note and the token prefix."
        ) q: String?
    ): ClientInvitationListResource {
        val clientAuth = authentication.clientAuthentication
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, invitationCollectionManager.clientCapabilities(), sort, q)
        val invitations = invitationCollectionManager.listInvitationsCreatedBy(
            createdById = clientAuth.clientId,
            criteria = criteria,
            pageParams = pageParams
        )
        return ClientInvitationListResource(
            invitations = invitations.items.map(invitationMapper::toResource),
            page = invitations.page,
            size = invitations.size,
            total = invitations.total
        )
    }

    @Operation(
        description = "Retrieve a single invitation created by this client.",
        tags = ["client"],
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
        @PathVariable @Parameter(description = "Unique identifier of the invitation.") invitationId: UUID
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
        @PathVariable @Parameter(description = "Unique identifier of the invitation to revoke.") invitationId: UUID
    ): ClientInvitationResource {
        val clientAuth = authentication.clientAuthentication
        invitationManager.findByIdOrNull(invitationId)
            ?.takeIf { it.createdById == clientAuth.clientId }
            .orNotFound()
        val invitation = invitationManager.revokeInvitation(invitationId)
        return invitationMapper.toResource(invitation)
    }
}
