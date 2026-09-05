package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminAudienceResourceMapper
import com.sympauthy.api.resource.admin.AdminAudienceListResource
import com.sympauthy.api.resource.admin.AdminAudienceResource
import com.sympauthy.api.util.PaginationUtil
import com.sympauthy.api.util.orNotFound
import com.sympauthy.business.manager.AudienceSearchManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_CONFIG_READ
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject

@Controller("/api/v1/admin/audiences")
@Secured(ADMIN_CONFIG_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONFIG_READ])
class AdminAudienceController(
    @Inject private val audienceSearchManager: AudienceSearchManager,
    @Inject private val audienceMapper: AdminAudienceResourceMapper,
    @Inject private val paginationUtil: PaginationUtil
) {

    @Operation(
        description = "Retrieve all configured audiences. Since audiences are defined in configuration files, " +
                "this endpoint exposes them as read-only resources. Audiences are ordered by identifier.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of audiences."),
            ApiResponse(responseCode = "400", description = "Invalid page or size."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get
    suspend fun listAudiences(
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(
            description = "Number of results per page. Defaults to the size this server is configured " +
                    "with, and may not exceed its configured maximum."
        ) size: Int?
    ): AdminAudienceListResource {
        val pageParams = paginationUtil.resolvePageParams(page, size)
        val audiences = audienceSearchManager.listAudiences(pageParams)
        return AdminAudienceListResource(
            audiences = audiences.items.map { audienceMapper.toResource(it.audience, it.clientCount) },
            page = audiences.page,
            size = audiences.size,
            total = audiences.total
        )
    }

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
        val audience = audienceSearchManager.findAudienceByIdOrNull(audienceId).orNotFound()
        return audienceMapper.toResource(audience.audience, audience.clientCount)
    }
}
