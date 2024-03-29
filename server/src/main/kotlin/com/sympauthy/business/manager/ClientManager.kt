package com.sympauthy.business.manager

import com.sympauthy.business.model.client.Client
import com.sympauthy.config.model.ClientsConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

@Singleton
class ClientManager(
    @Inject private val uncheckedClientsConfig: Flow<ClientsConfig>
) {

    suspend fun listClients(): List<Client> {
        return uncheckedClientsConfig.firstOrNull()?.orThrow()?.clients ?: emptyList()
    }

    /**
     * Return the [Client] identified by [clientId] if the [clientSecret] matches the one configured.
     * Otherwise, return null whether no client matches or the secret does not match.
     */
    suspend fun authenticateClient(clientId: String, clientSecret: String): Client? {
        return uncheckedClientsConfig.firstOrNull()?.orThrow()
            ?.clients
            ?.firstOrNull { it.id == clientId && it.secret == clientSecret }
    }
}
