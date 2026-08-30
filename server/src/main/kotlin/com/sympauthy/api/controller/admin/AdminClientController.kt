package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminClientResourceMapper
import com.sympauthy.api.resource.admin.AdminClientListResource
import com.sympauthy.api.resource.admin.AdminClientResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.orderedPage
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.ClientManager
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

@Controller("/api/v1/admin/clients")
@Secured(ADMIN_CONFIG_READ)
@SecurityRequirement(name = "admin", scopes = [AdminScopeId.CONFIG_READ])
class AdminClientController(
    @Inject private val clientManager: ClientManager,
    @Inject private val clientMapper: AdminClientResourceMapper
) {

    @Operation(
        description = "Retrieve all configured clients. Since clients are defined in configuration files, " +
                "this endpoint exposes them as read-only resources. Client secrets are never included. " +
                "Ordered by identifier.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Paginated list of clients."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            )
        ]
    )
    @Get
    suspend fun listClients(
        @QueryValue @Parameter(description = "Zero-indexed page number.") page: Int?,
        @QueryValue @Parameter(description = "Number of results per page.") size: Int?
    ): AdminClientListResource {
        val pageParams = resolvePageParams(page, size)
        val clients = clientManager.listClients()
        val paged = clients
            .orderedPage(pageParams, compareBy { it.id })
            .map(clientMapper::toSummaryResource)
        return AdminClientListResource(
            clients = paged,
            page = pageParams.page,
            size = pageParams.size,
            total = clients.size
        )
    }

    @Operation(
        description = "Retrieve details for a specific client by its identifier.",
        tags = ["admin"],
        responses = [
            ApiResponse(responseCode = "200", description = "Client details."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(
                responseCode = "403",
                description = "The access token does not include the required scope: admin:config:read."
            ),
            ApiResponse(responseCode = "404", description = "No client found with the given identifier.")
        ]
    )
    @Get("/{clientId}")
    suspend fun getClient(
        @PathVariable @Parameter(description = "Unique identifier of the client.") clientId: String
    ): AdminClientResource {
        val client = clientManager.findClientByIdOrNull(clientId).orNotFound()
        return clientMapper.toResource(client)
    }
}
