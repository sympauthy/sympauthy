package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.ADMIN_SURFACE
import com.sympauthy.api.mapper.admin.AdminClaimResourceMapper
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.resource.admin.AdminClaimListResource
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
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
import com.sympauthy.business.manager.collection.ClaimCollectionManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_CONFIG_READ
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
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

private const val FORBIDDEN_DESCRIPTION =
    "- `forbidden`: the access token does not carry the scope `admin:config:read`."

@Controller("$ADMIN_SURFACE/claims")
@Secured(ADMIN_CONFIG_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONFIG_READ])
class AdminClaimController(
    @Inject private val claimCollectionManager: ClaimCollectionManager,
    @Inject private val claimMapper: AdminClaimResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve the claims this server knows, including the ones the configuration turned off. " +
                "Claims are ordered with the enabled ones first, then by identifier, unless another order is " +
                "asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "$ADMIN_SURFACE/claims/capabilities.",
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
            ApiResponse(responseCode = "200", description = "Paginated list of claims."),
            ApiResponse(responseCode = "400", description = COLLECTION_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION)
        ]
    )
    @Get
    suspend fun listClaims(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " It matches the identifier of the claim."
        ) q: String?
    ): AdminClaimListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, claimCollectionManager.capabilities(), sort, q)
        val claims = claimCollectionManager.listClaims(criteria, pageParams)
        return AdminClaimListResource(
            claims = claims.items.map(claimMapper::toResource),
            page = claims.page,
            size = claims.size,
            total = claims.total
        )
    }

    @Operation(
        description = "Retrieve what the claim collection accepts: the fields it filters on and the operators " +
                "each accepts, the fields it orders on and searches, and its own order. " +
                "Each `name` is in the language the request asks for, and may be reworded in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the claim collection accepts."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION)
        ]
    )
    @Get("/capabilities")
    suspend fun getClaimCapabilities(request: HttpRequest<*>): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(claimCollectionManager.capabilities(), request.locale.orDefault())
}
