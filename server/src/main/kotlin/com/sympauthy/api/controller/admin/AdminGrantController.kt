package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.ADMIN_SURFACE
import com.sympauthy.api.exception.recoverableHttpExceptionOf
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.mapper.admin.AdminGrantResourceMapper
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.resource.admin.AdminGrantDetailResource
import com.sympauthy.api.resource.admin.AdminGrantListResource
import com.sympauthy.api.resource.admin.AdminRevokeGrantsInputResource
import com.sympauthy.api.resource.admin.AdminRevokedGrantsResource
import com.sympauthy.api.util.BODY_BAD_REQUEST_DESCRIPTION
import com.sympauthy.api.util.BODY_TOO_LARGE_RESPONSE_DESCRIPTION
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
import com.sympauthy.business.manager.auth.GrantManager
import com.sympauthy.business.manager.collection.GrantCollectionManager
import com.sympauthy.business.model.grant.GrantRevocationCriteria
import com.sympauthy.business.model.grant.GrantRevokedBy
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_GRANTS_READ
import com.sympauthy.security.SecurityRule.ADMIN_GRANTS_WRITE
import com.sympauthy.security.userId
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
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

@Controller("$ADMIN_SURFACE/grants")
class AdminGrantController(
    @Inject private val grantManager: GrantManager,
    @Inject private val grantCollectionManager: GrantCollectionManager,
    @Inject private val grantMapper: AdminGrantResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve the grants this server holds, whoever they were opened for, revoked, expired " +
                "and lapsed ones included until they are deleted. A user's grants are the ones filtered on " +
                "`user_id`. " +
                "Grants are ordered by the date they were opened, oldest first, unless another order is asked " +
                "for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "$ADMIN_SURFACE/grants/capabilities.",
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
            ApiResponse(responseCode = "200", description = "Paginated list of grants."),
            ApiResponse(responseCode = "400", description = COLLECTION_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:grants:read`."
            )
        ]
    )
    @Get
    @Secured(ADMIN_GRANTS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.GRANTS_READ])
    suspend fun listGrants(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " It matches the identifier claims of the user the " +
                    "grant was opened for, the identifier of its client and the identifier of its audience."
        ) q: String?
    ): AdminGrantListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, grantCollectionManager.capabilities(), sort, q)
        val grants = grantCollectionManager.listGrants(criteria, pageParams)
        return AdminGrantListResource(
            grants = grants.items.map(grantMapper::toSummaryResource),
            page = grants.page,
            size = grants.size,
            total = grants.total
        )
    }

    @Operation(
        description = "Retrieve what the grant collection accepts: the fields it filters on and the operators " +
                "each accepts, the fields it orders on and searches, and its own order. " +
                "Each `name` is in the language the request asks for, and may be reworded in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the grant collection accepts."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:grants:read`."
            )
        ]
    )
    @Get("/capabilities")
    @Secured(ADMIN_GRANTS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.GRANTS_READ])
    suspend fun getGrantCapabilities(request: HttpRequest<*>): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(grantCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Retrieve a grant, with everything it records.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "The grant."),
            ApiResponse(responseCode = "400", description = PARAMETER_MALFORMED_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:grants:read`."
            ),
            ApiResponse(
                responseCode = "404",
                description = "- `not_found`: this server holds no grant with this identifier, which is also " +
                        "the case of a grant already deleted."
            )
        ]
    )
    @Get("/{grantId}")
    @Secured(ADMIN_GRANTS_READ)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.GRANTS_READ])
    suspend fun getGrant(
        @PathVariable @Parameter(description = "Identifier of the grant.") grantId: UUID
    ): AdminGrantDetailResource {
        val grant = grantCollectionManager.findGrantOrNull(grantId).orNotFound()
        return grantMapper.toDetailResource(grant)
    }

    @Operation(
        description = "Revoke every grant the body names, whoever they were opened for. Every token issued " +
                "under each of them stops working at once, and so does every grant a client obtained by " +
                "exchanging one of their tokens, which `actor_grant_id` finds. A grant that is no longer " +
                "`live` is left as it is, so it keeps the revocation that came first. Each grant revoked " +
                "is listed at $ADMIN_SURFACE/grants as `revoked`, by `admin`, naming who revoked it.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "How many grants were revoked."),
            ApiResponse(
                responseCode = "400",
                description = "- `grant.revoke.no_criterion`: the body sends none of `grant_ids`, `user_ids`, " +
                        "`client_ids` and `audience_ids`.\n" +
                        BODY_BAD_REQUEST_DESCRIPTION
            ),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(
                responseCode = "403",
                description = "- `forbidden`: the access token does not carry the scope `admin:grants:write`."
            ),
            ApiResponse(responseCode = "413", description = BODY_TOO_LARGE_RESPONSE_DESCRIPTION)
        ]
    )
    @Post("/revoke")
    @Secured(ADMIN_GRANTS_WRITE)
    @SecurityRequirement(name = "admin", scopes = [AdminScopeId.GRANTS_WRITE])
    suspend fun revokeGrants(
        @Body input: AdminRevokeGrantsInputResource,
        authentication: Authentication
    ): AdminRevokedGrantsResource {
        val grantsRevoked = grantManager.revokeGrants(input.toCriteria(), GrantRevokedBy.ADMIN, authentication.userId)
        return AdminRevokedGrantsResource(grantsRevoked = grantsRevoked)
    }
}

/**
 * The grants this body names, refused with a recoverable `grant.revoke.no_criterion` where it sends no set,
 * rather than read as every grant.
 */
private fun AdminRevokeGrantsInputResource.toCriteria(): GrantRevocationCriteria {
    if (grantIds == null && userIds == null && clientIds == null && audienceIds == null) {
        throw recoverableHttpExceptionOf(
            HttpStatus.BAD_REQUEST, "grant.revoke.no_criterion", "description.grant.revoke.no_criterion"
        )
    }
    return GrantRevocationCriteria(
        grantIds = grantIds?.toSet(),
        userIds = userIds?.toSet(),
        clientIds = clientIds?.toSet(),
        audienceIds = audienceIds?.toSet()
    )
}
