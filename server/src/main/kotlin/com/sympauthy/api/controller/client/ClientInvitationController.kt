package com.sympauthy.api.controller.client

import com.sympauthy.api.mapper.client.ClientInvitationResourceMapper
import com.sympauthy.api.resource.client.ClientCreateInvitationInputResource
import com.sympauthy.api.resource.client.ClientCreatedInvitationResource
import com.sympauthy.api.resource.client.ClientInvitationListResource
import com.sympauthy.api.resource.client.ClientInvitationResource
import com.sympauthy.api.util.CLAIM_VALUE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.COLLECTION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_NAME
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.SEARCH_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SORT_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
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
    @Inject private val invitationManager: InvitationManager,
    @Inject private val invitationCollectionManager: InvitationCollectionManager,
    @Inject private val invitationMapper: ClientInvitationResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    companion object {
        private const val INVITATION_ID = "Keep the invitation with this identifier. When absent, any identifier."
        private const val STATUS = "Keep the invitations in this status. When absent, any status."
        private const val TOKEN_PREFIX = "Keep the invitations whose token prefix is exactly this value. When " +
            "absent, any token prefix."
        private const val NOTE = "Keep the invitations whose note is exactly this value. When absent, any " +
            "note, or none."
        private const val CREATED_AT = "Keep the invitations created at this date and time, in UTC and with no " +
            "zone. When absent, any date and time."
        private const val EXPIRES_AT = "Keep the invitations expiring at this date and time, in UTC and with no " +
            "zone. When absent, any date and time."
        private const val CONSUMED_AT = "Keep the invitations consumed at this date and time, in UTC and with no " +
            "zone. When absent, any date and time, or none."
        private const val REVOKED_AT = "Keep the invitations revoked at this date and time, in UTC and with no " +
            "zone. When absent, any date and time, or none."
        private const val CONSUMED_BY = "Keep the invitation consumed by the user with this identifier. When " +
            "absent, any user, or none."
        private const val INVITATION_NOT_FOUND =
            "- `not_found`: no invitation has this identifier, or this client did not create it."
    }

    @Operation(
        description = "Create an invitation to this client's audience. This response is the only one carrying " +
            "the invitation's token.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "201", description = "The invitation, with its token."),
            ApiResponse(
                responseCode = "400",
                description = "- `invitation.unknown_claim`: `claims` names a claim that is not configured or " +
                    "not enabled.\n" +
                    "- `invitation.claim_of_another_audience`: `claims` names a claim restricted to another " +
                    "audience.\n" +
                    "- `invitation.claim_not_writable`: `claims` names a claim this client may not write.\n" +
                    CLAIM_VALUE_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `invitations:write`."
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
        val client = clientAuth.client
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
        description = "Retrieve the invitations this client created. Invitations are ordered by creation date, " +
            "oldest first, unless another order is asked for.",
        tags = ["client"],
        parameters = [
            Parameter(name = "id", `in` = QUERY, description = INVITATION_ID, schema = Schema(type = "string")),
            Parameter(
                name = "status",
                `in` = QUERY,
                description = STATUS,
                schema = Schema(type = "string", allowableValues = ["pending", "consumed", "revoked", "expired"])
            ),
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
            ApiResponse(responseCode = "400", description = COLLECTION_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `invitations:read`."
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
        @QueryValue @Parameter(
            description = SORT_PARAMETER_DESCRIPTION + " Here, the keys are `status`, `created_at`, `expires_at`, " +
                "`consumed_at` and `revoked_at`."
        ) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " Here, the note and the token prefix."
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
        description = "Retrieve an invitation this client created.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "200", description = "The invitation."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `invitations:read`."
            ),
            ApiResponse(responseCode = "404", description = INVITATION_NOT_FOUND)
        ]
    )
    @Get("/{invitationId}")
    @Secured(CLIENT_INVITATIONS_READ)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.INVITATIONS_READ])
    suspend fun getInvitation(
        authentication: Authentication,
        @PathVariable @Parameter(description = "Identifier of the invitation.") invitationId: UUID
    ): ClientInvitationResource {
        val clientAuth = authentication.clientAuthentication
        val invitation = invitationManager.findByIdOrNull(invitationId)
            ?.takeIf { it.createdById == clientAuth.clientId }
            .orNotFound()
        return invitationMapper.toResource(invitation)
    }

    @Operation(
        description = "Revoke a pending invitation this client created. Its token can no longer be used.",
        tags = ["client"],
        responses = [
            ApiResponse(responseCode = "200", description = "The invitation, now `revoked`."),
            ApiResponse(
                responseCode = "400",
                description = "- `invitation.cannot_revoke`: the invitation is not `pending`."
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `invitations:write`."
            ),
            ApiResponse(responseCode = "404", description = INVITATION_NOT_FOUND)
        ]
    )
    @Post("/{invitationId}/revoke")
    @Secured(CLIENT_INVITATIONS_WRITE)
    @SecurityRequirement(name = "client", scopes = [BuiltInClientScopeId.INVITATIONS_WRITE])
    suspend fun revokeInvitation(
        authentication: Authentication,
        @PathVariable @Parameter(description = "Identifier of the invitation to revoke.") invitationId: UUID
    ): ClientInvitationResource {
        val clientAuth = authentication.clientAuthentication
        invitationManager.findByIdOrNull(invitationId)
            ?.takeIf { it.createdById == clientAuth.clientId }
            .orNotFound()
        val invitation = invitationManager.revokeInvitation(invitationId)
        return invitationMapper.toResource(invitation)
    }
}
