package com.sympauthy.business.manager

import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.page.Page
import com.sympauthy.business.model.page.PageParams
import com.sympauthy.business.model.page.orderedPage
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager responsible for reading the clients this deployment configured.
 *
 * The clients are configuration held in memory, so a page of them is a slice of a list rather than
 * a query.
 */
@Singleton
class ClientSearchManager(
    @Inject private val clientManager: ClientManager
) {

    /**
     * Read the page [pageParams] names of every configured [Client], ordered by identifier.
     */
    suspend fun listClients(pageParams: PageParams): Page<Client> {
        return clientManager.listClients()
            .orderedPage(pageParams, compareBy { it.id })
    }
}
