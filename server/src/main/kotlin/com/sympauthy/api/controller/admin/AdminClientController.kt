package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminClientResourceMapper
import com.sympauthy.api.resource.admin.AdminClientListResource
import com.sympauthy.api.resource.admin.AdminClientResource
import com.sympauthy.api.util.orNotFound
import com.sympauthy.api.util.resolvePageParams
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.security.SecurityRule.ADMIN_CLIENTS_READ
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.inject.Inject

@Controller("/api/v1/admin/clients")
@Secured(ADMIN_CLIENTS_READ)
class AdminClientController(
    @Inject private val clientManager: ClientManager,
    @Inject private val clientMapper: AdminClientResourceMapper
) {

    @Operation(
        description = "Retrieve all configured clients. Since clients are defined in configuration files, this endpoint exposes them as read-only resources. Client secrets are never included.",
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
            ApiResponse(responseCode = "200", description = "Paginated list of clients."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(responseCode = "403", description = "The access token does not include the required scope: admin:clients:read.")
        ]
    )
    @Get
    suspend fun listClients(
        @QueryValue page: Int?,
        @QueryValue size: Int?
    ): AdminClientListResource {
        val (page, size) = resolvePageParams(page, size)
        val clients = clientManager.listClients()
        val paged = clients
            .drop(page * size)
            .take(size)
            .map(clientMapper::toResource)
        return AdminClientListResource(
            clients = paged,
            page = page,
            size = size,
            total = clients.size
        )
    }

    @Operation(
        description = "Retrieve details for a specific client by its identifier.",
        tags = ["admin"],
        parameters = [
            Parameter(
                name = "clientId",
                description = "Unique identifier of the client.",
                schema = Schema(type = "string")
            )
        ],
        responses = [
            ApiResponse(responseCode = "200", description = "Client details."),
            ApiResponse(responseCode = "401", description = "Missing or invalid access token."),
            ApiResponse(responseCode = "403", description = "The access token does not include the required scope: admin:clients:read."),
            ApiResponse(responseCode = "404", description = "No client found with the given identifier.")
        ]
    )
    @Get("/{clientId}")
    suspend fun getClient(
        @PathVariable clientId: String
    ): AdminClientResource {
        val client = clientManager.findClientByIdOrNull(clientId).orNotFound()
        return clientMapper.toResource(client)
    }
}