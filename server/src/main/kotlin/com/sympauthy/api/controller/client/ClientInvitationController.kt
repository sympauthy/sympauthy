package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientInvitationResourceMapper
import com.sympauthy.api.resource.client.ClientCreateInvitationInputResource
import com.sympauthy.api.resource.client.ClientCreatedInvitationResource
import com.sympauthy.api.resource.client.ClientInvitationListResource
import com.sympauthy.api.resource.client.ClientInvitationResource
import com.sympauthy.api.util.FILTER_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_NAME
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.SEARCH_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SORT_PARAMETER_DESCRIPTION
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
import io.swagger.v3.oas.annotations.enums.Explode
import io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY
import io.swagger.v3.oas.annotations.enums.ParameterStyle
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
    @Inject private val invitationCollectionManager: InvitationCollectionManager,
    @Inject private val invitationMapper: ClientInvitationResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    companion object {
        private const val INVITATION_ID = "Keep the invitations this identifier names."
        private const val STATUS = "Keep the invitations in this state: pending, consumed, revoked or expired."
        private const val TOKEN_PREFIX = "Keep the invitations whose token begins with this."
        private const val NOTE = "Keep the invitations carrying this note."
        private const val CREATED_AT = "Keep the invitations created at this moment, ISO-8601 with no zone."
        private const val EXPIRES_AT = "Keep the invitations expiring at this moment, ISO-8601 with no zone."
        private const val CONSUMED_AT = "Keep the invitations consumed at this moment, ISO-8601 with no zone."
        private const val REVOKED_AT = "Keep the invitations revoked at this moment, ISO-8601 with no zone."
        private const val CONSUMED_BY = "Keep the invitation the account this identifier names consumed."
    }

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
                "revoked_at, consumed_by_user_id and id, and searched with q across the note and the " +
                "token prefix.",
        tags = ["client"],
        parameters = [
            Parameter(name = "id", `in` = QUERY, description = INVITATION_ID, schema = Schema(type = "string")),
            Parameter(name = "status", `in` = QUERY, description = STATUS, schema = Schema(type = "string")),
            Parameter(
                name = "token_prefix",
                `in` = QUERY,
                description = TOKEN_PREFIX,
                schema = Schema(type = "string")
            ),
            Parameter(name = "note", `in` = QUERY, description = NOTE, schema = Schema(type = "string")),
            Parameter(name = "created_at", `in` = QUERY, description = CREATED_AT, schema = Schema(type = "string")),
            Parameter(name = "expires_at", `in` = QUERY, description = EXPIRES_AT, schema = Schema(type = "string")),
            Parameter(name = "consumed_at", `in` = QUERY, description = CONSUMED_AT, schema = Schema(type = "string")),
            Parameter(name = "revoked_at", `in` = QUERY, description = REVOKED_AT, schema = Schema(type = "string")),
            Parameter(
                name = "consumed_by_user_id",
                `in` = QUERY,
                description = CONSUMED_BY,
                schema = Schema(type = "string")
            ),
            Parameter(
                name = FILTER_PARAMETER_NAME,
                `in` = QUERY,
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
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " Here that is the note and the token prefix."
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
