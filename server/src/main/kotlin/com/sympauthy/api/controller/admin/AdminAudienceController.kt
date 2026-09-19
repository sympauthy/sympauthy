package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminAudienceResourceMapper
import com.sympauthy.api.mapper.admin.AdminCollectionCapabilitiesResourceMapper
import com.sympauthy.api.resource.admin.AdminAudienceListResource
import com.sympauthy.api.resource.admin.AdminAudienceResource
import com.sympauthy.api.resource.admin.AdminCollectionCapabilitiesResource
import com.sympauthy.api.util.FILTER_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.FILTER_PARAMETER_NAME
import com.sympauthy.api.util.PAGE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.SEARCH_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SIZE_PARAMETER_DESCRIPTION
import com.sympauthy.api.util.SORT_PARAMETER_DESCRIPTION
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

@Controller("/api/v1/admin/audiences")
@Secured(ADMIN_CONFIG_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONFIG_READ])
class AdminAudienceController(
    @Inject private val audienceCollectionManager: AudienceCollectionManager,
    @Inject private val audienceMapper: AdminAudienceResourceMapper,
    @Inject private val capabilitiesMapper: AdminCollectionCapabilitiesResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve all configured audiences. Since audiences are defined in configuration files, " +
                "this endpoint exposes them as read-only resources. Audiences are ordered by identifier " +
                "unless another order is asked for. " +
                "Which fields this collection can be filtered, ordered and searched on is published at " +
                "/api/v1/admin/audiences/capabilities.",
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
            ApiResponse(
                responseCode = "400",
                description = "Invalid page or size, an unknown field, an operator the field does not " +
                        "accept, or a value it does not hold."
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get
    suspend fun listAudiences(
        request: HttpRequest<*>,
        @QueryValue @Parameter(description = PAGE_PARAMETER_DESCRIPTION) page: Int?,
        @QueryValue @Parameter(description = SIZE_PARAMETER_DESCRIPTION) size: Int?,
        @QueryValue @Parameter(description = SORT_PARAMETER_DESCRIPTION) sort: String?,
        @QueryValue @Parameter(description = SEARCH_PARAMETER_DESCRIPTION) q: String?
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
        description = "Retrieve what the audience collection accepts: the fields it filters on and the " +
                "operators each admits, the fields it orders on, the fields a free-text search matches " +
                "against, and the order it takes when none is asked for. " +
                "The names it carries are read in the language the request asked for and may be reworded " +
                "in any release.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "What the collection accepts."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get("/capabilities")
    suspend fun getAudienceCapabilities(request: HttpRequest<*>): AdminCollectionCapabilitiesResource =
        capabilitiesMapper.toResource(audienceCollectionManager.capabilities(), request.locale.orDefault())

    @Operation(
        description = "Retrieve details for a specific audience by its identifier.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Audience details."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            ),
            ApiResponse(responseCode = "404", description = "No audience found with the given identifier.")
        ]
    )
    @Get("/{audienceId}")
    suspend fun getAudience(
        @PathVariable @Parameter(description = "Unique identifier of the audience.") audienceId: String
    ): AdminAudienceResource {
        val audience = audienceCollectionManager.findAudienceByIdOrNull(audienceId).orNotFound()
        return audienceMapper.toResource(audience.audience, audience.clientCount)
    }
}
