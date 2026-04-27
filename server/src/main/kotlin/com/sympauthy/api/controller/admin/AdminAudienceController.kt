package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminAudienceResourceMapper
import com.sympauthy.api.resource.admin.AdminAudienceListResource
import com.sympauthy.api.resource.admin.AdminAudienceResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.AudienceManager
import com.sympauthy.business.model.oauth2.AdminScopeId
import com.sympauthy.security.SecurityRule.ADMIN_CONFIG_READ
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.inject.Inject

@Controller("/api/v1/admin/audiences")
@Secured(ADMIN_CONFIG_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONFIG_READ])
class AdminAudienceController(
    @Inject private val audienceManager: AudienceManager,
    @Inject private val audienceMapper: AdminAudienceResourceMapper
) {

    @Operation(
        description = "Retrieve all configured audiences. Since audiences are defined in configuration files, this endpoint exposes them as read-only resources.",
        tags = ["admin"],
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
            ApiResponse(responseCode = "200", description = "Paginated list of audiences."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get
    suspend fun listAudiences(
        @QueryValue page: Int?,
        @QueryValue size: Int?
    ): AdminAudienceListResource {
        val (page, size) = resolvePageParams(page, size)
        val audiences = audienceManager.listAudiences()
        val paged = audiences
            .drop(page * size)
            .take(size)
            .map(audienceMapper::toResource)
        return AdminAudienceListResource(
            audiences = paged,
            page = page,
            size = size,
            total = audiences.size
        )
    }

    @Operation(
        description = "Retrieve details for a specific audience by its identifier.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "audienceId",
                description = "Unique identifier of the audience.",
                schema = Schema(type = "string")
            )
        ],
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
        @PathVariable audienceId: String
    ): AdminAudienceResource {
        val audience = audienceManager.findAudienceByIdOrNull(audienceId).orNotFound()
        return audienceMapper.toResource(audience)
    }
}
