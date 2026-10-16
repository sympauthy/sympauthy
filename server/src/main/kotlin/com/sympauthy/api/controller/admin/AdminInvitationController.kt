package com.sympauthy.api.controller.admin

import com.sympauthy.api.exception.recoverableHttpExceptionOf
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminInvitationResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminCreateInvitationInputResource
import com.sympauthy.api.resource.admin.AdminCreatedInvitationResource
import com.sympauthy.api.resource.admin.AdminInvitationListResource
import com.sympauthy.api.resource.admin.AdminInvitationResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.CLAIM_VALUE_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.COLLECTION_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_NAME
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.SEARCH_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SORT_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.UNAUTHORIZED_RESPONSE_DESCRIPTION
import com.sympauthy.api.util.collectionCriteriaOf
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.parseInvitationExpiresAt
import com.sympauthy.business.manager.AudienceManager
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
import io.swagger.v3.oas.annotations.enums.Explode
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.enums.ParameterStyle
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject
import java.util.*

@Controller("/api/v1/admin/invitations")
class AdminInvitationController(
    @Inject private val audienceManager: AudienceManager,
    @Inject private val invitationManager: InvitationManager,
    @Inject private val invitationCollectionManager: InvitationCollectionManager,
    @Inject private val invitationMapper: AdminInvitationResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Create an invitation to an audience. This response is the only one carrying the " +
                "invitation's token.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "201", description = "The invitation, with its token."),
            ApiResponse(
                responseCode = "400",
                description = "- `invitation.expires_at.malformed`: `expires_at` is not a date and time with no " +
                        "zone, such as `2026-12-01T00:00:00`.\n" +
                        "- `invitation.unknown_audience`: `audience_id` names an audience that is not " +
                        "configured.\n" +
                        "- `invitation.unknown_claim`: `claims` names a claim that is not configured or " +
                        "not enabled.\n" +
                        "- `invitation.claim_of_another_audience`: `claims` names a claim restricted to another " +
                        "audience.\n" +
                        CLAIM_VALUE_BAD_REQUEST_DESCRIPTION + "\n" +
                        BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:invitations:write`."
            ),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ]
    )
    @Post
    @Status(HttpStatus.CREATED)
    @Secured(ADMIN_INVITATIONS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_WRITE])
    suspend fun createInvitation(
        @Body input: AdminCreateInvitationInputResource
    ): AdminCreatedInvitationResource {
        val expiresAt = parseInvitationExpiresAt(input.expiresAt)
        val audience = audienceManager.findAudienceByIdOrNull(input.audienceId) ?: throw recoverableHttpExceptionOf(
            HttpStatus.BAD_REQUEST, "invitation.unknown_audience", "description.invitation.unknown_audience",
            "audience" to input.audienceId
        )
        val (invitation, rawToken) = invitationManager.createInvitation(
            audience = audience,
            claims = input.claims,
            note = input.note,
            expiresAt = expiresAt,
            createdBy = InvitationCreatedBy.ADMIN,
        )
        return invitationMapper.toCreatedResource(invitation, rawToken)
    }

    @Operation(
        description = "Retrieve the invitations, whoever created them. " +
                "Invitations are ordered by creation date, oldest first, unless another order is asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "/api/v1/admin/invitations/capabilities.",
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
            ApiResponse(responseCode = "200", description = "Paginated list of invitations."),
            ApiResponse(responseCode = "400", description = COLLECTION_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:invitations:read`."
            )
        ]
    )
    @Get
    @Secured(ADMIN_INVITATIONS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_READ])
    suspend fun listInvitations(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " It matches the token prefix and the note of the " +
                    "invitation."
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
                "operators each accepts, the fields it orders on and searches, and its own order. " +
                "Each `name` is in the language the request asks for, and may be reworded in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the invitation collection accepts."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:invitations:read`."
            )
        ]
    )
    @Get("/capabilities")
    @Secured(ADMIN_INVITATIONS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_READ])
    suspend fun getInvitationCapabilities(request: HttpRequest<*>): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(invitationCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Retrieve an invitation.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "The invitation."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:invitations:read`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: no invitation has this identifier."
            )
        ]
    )
    @Get("/{invitationId}")
    @Secured(ADMIN_INVITATIONS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_READ])
    suspend fun getInvitation(
        @PathVariable @Parameter(description = "Identifier of the invitation.") invitationId: UUID
    ): AdminInvitationResource {
        val invitation = invitationManager.findByIdOrNull(invitationId).orNotFound()
        return invitationMapper.toResource(invitation)
    }

    @Operation(
        description = "Revoke a pending invitation. Its token can no longer be used.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "The invitation, now `revoked`."),
            ApiResponse(
                responseCode = "400",
                description = "- `invitation.not_found`: no invitation has this identifier.\n" +
                        "- `invitation.cannot_revoke`: the invitation is not `pending`.\n" +
                        PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:invitations:write`."
            )
        ]
    )
    @Post("/{invitationId}/revoke")
    @Secured(ADMIN_INVITATIONS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.INVITATIONS_WRITE])
    suspend fun revokeInvitation(
        @PathVariable @Parameter(description = "Identifier of the invitation to revoke.") invitationId: UUID
    ): AdminInvitationResource {
        val invitation = invitationManager.revokeInvitation(invitationId)
        return invitationMapper.toResource(invitation)
    }
}
