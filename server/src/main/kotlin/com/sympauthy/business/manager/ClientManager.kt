package com.sympauthy.business.manager

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.config.model.ClientsConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

@Singleton
open class ClientManager(
    @Inject private val uncheckedClientsConfig: Flow<ClientsConfig>
) {

    suspend fun listClients(): List<Client> {
        return uncheckedClientsConfig.firstOrNull()?.orThrow()?.clients ?: emptyList()
    }

    /**
     * Return the [Client] identified by [id]. Otherwise, return null if no client matches.
     */
    suspend fun findClientByIdOrNull(id: String): Client? {
        return listClients().firstOrNull { it.id == id }
    }

    /**
     * Return the [Client] identified by [id] or throw a non-recoverable business exception
     * ```client.invalid_client_id```.
     */
    suspend fun findClientById(id: String): Client {
        return findClientByIdOrNull(id) ?: throw businessExceptionOf(
            detailsId = "client.invalid_client_id",
            values = arrayOf("clientId" to id)
        )
    }

    /**
     * Parse the [uncheckedClientId] and return the corresponding [Client] or throw a non-recoverable business exception.
     */
    suspend fun parseRequestedClient(uncheckedClientId: String?): Client {
        if (uncheckedClientId.isNullOrBlank()) {
            throw businessExceptionOf(
                detailsId = "client.parse_requested.missing",
            )
        }
        return findClientById(uncheckedClientId)
    }

    /**
     * Return the [Client] identified by [clientId] if the [clientSecret] matches the one configured.
     * Otherwise, return null whether no client matches or the secret does not match.
     */
    suspend fun authenticateClient(clientId: String, clientSecret: String): Client? {
        return listClients().firstOrNull { it.id == clientId && it.secret == clientSecret }
    }
}
