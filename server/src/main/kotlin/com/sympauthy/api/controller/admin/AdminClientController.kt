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
import jakarta.inject.Inject

@Controller("/api/v1/admin/clients")
@Secured(ADMIN_CLIENTS_READ)
class AdminClientController(
    @Inject private val clientManager: ClientManager,
    @Inject private val clientMapper: AdminClientResourceMapper
) {

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

    @Get("/{clientId}")
    suspend fun getClient(
        @PathVariable clientId: String
    ): AdminClientResource {
        val client = clientManager.findClientByIdOrNull(clientId).orNotFound()
        return clientMapper.toResource(client)
    }
}
