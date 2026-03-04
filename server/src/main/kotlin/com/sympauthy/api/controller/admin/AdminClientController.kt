package com.sympauthy.api.controller.admin

import com.sympauthy.api.mapper.admin.AdminClientResourceMapper
import com.sympauthy.api.resource.admin.AdminClientResource
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.security.SecurityRule.ADMIN_CLIENTS_READ
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import jakarta.inject.Inject

@Controller("/api/v1/admin/clients")
@Secured(ADMIN_CLIENTS_READ)
class AdminClientController(
    @Inject private val clientManager: ClientManager,
    @Inject private val clientMapper: AdminClientResourceMapper
) {

    @Get
    suspend fun listClients(): List<AdminClientResource> {
        return clientManager.listClients().map(clientMapper::toResource)
    }
}
