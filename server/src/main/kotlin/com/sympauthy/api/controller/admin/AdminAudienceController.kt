package com.sympauthy.api.controller.admin

import com.sympauthy.api.controller.ADMIN_SURFACE
import com.sympauthy.api.mapper.admin.AdminAudienceResourceMapper
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.resource.admin.AdminAudienceListResource
import com.sympauthy.api.resource.admin.AdminAudienceResource
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
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.collection.AudienceCollectionManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_CONFIG_READ
import com.sympauthy.util.orDefault
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
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

@Controller("$ADMIN_SURFACE/audiences")
@Secured(ADMIN_CONFIG_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONFIG_READ])
class AdminAudienceController(
    @Inject private val audienceCollectionManager: AudienceCollectionManager,
    @Inject private val audienceMapper: AdminAudienceResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve the audiences the configuration declares, each with the number of its clients. " +
                "Audiences are ordered by identifier, unless another order is asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "$ADMIN_SURFACE/audiences/capabilities.",
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
            ApiResponse(responseCode = "200", description = "Paginated list of audiences."),
            ApiResponse(responseCode = "400", description = COLLECTION_BAD_REQUEST_DESCRIPTION),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION)
        ]
    )
    @Get
    suspend fun listAudiences(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(
            description = SEARCH_PARAMETER_DESCRIPTION + " It matches the identifier of the audience."
        ) q: String?
    ): AdminAudienceListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val criteria = collectionCriteriaOf(request, audienceCollectionManager.capabilities(), sort, q)
        val audiences = audienceCollectionManager.listAudiences(criteria, pageParams)
        return AdminAudienceListResource(
            audiences = audiences.items.map { audienceMapper.toResource(it.audience, it.clientCount) },
            page = audiences.page,
            size = audiences.size,
            total = audiences.total
        )
    }

    @Operation(
        description = "Retrieve what the audience collection accepts: the fields it filters on and the operators " +
                "each accepts, the fields it orders on and searches, and its own order. " +
                "Each `name` is in the language the request asks for, and may be reworded in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the audience collection accepts."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION)
        ]
    )
    @Get("/capabilities")
    suspend fun getAudienceCapabilities(request: HttpRequest<*>): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(audienceCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Retrieve an audience, with the number of its clients.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "The audience."),
            ApiResponse(responseCode = "401", description = UNAUTHORIZED_RESPONSE_DESCRIPTION),
            ApiResponse(responseCode = "403", description = FORBIDDEN_DESCRIPTION),
            ApiResponse(responseCode = "404", description = "- `not_found`: no audience has this identifier.")
        ]
    )
    @Get("/{audienceId}")
    suspend fun getAudience(
        @PathVariable @Parameter(description = "Identifier of the audience.") audienceId: String
    ): AdminAudienceResource {
        val audience = audienceCollectionManager.findAudienceByIdOrNull(audienceId).orNotFound()
        return audienceMapper.toResource(audience.audience, audience.clientCount)
    }
}
