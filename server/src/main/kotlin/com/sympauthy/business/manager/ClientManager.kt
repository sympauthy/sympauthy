package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
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
     * Return the number of clients grouped by their audience identifier.
     */
    suspend fun countClientsByAudienceId(): Map<String, Int> {
        return listClients().groupBy { it.audience.id }.mapValues { it.value.size }
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
            throw BusinessException(
                recoverable = false,
                detailsId = "client.parse_requested.missing",
                descriptionId = "description.client.parse_requested.missing"
            )
        }
        return findClientByIdOrNull(uncheckedClientId) ?: throw BusinessException(
            recoverable = false,
            detailsId = "client.parse_requested.invalid_client_id",
            descriptionId = "description.client.parse_requested.invalid_client_id",
            values = mapOf("clientId" to uncheckedClientId)
        )
    }

    /**
     * Return the [Client] identified by [id] only if it is a public client.
     * Otherwise, return null.
     */
    suspend fun findPublicClientByIdOrNull(id: String): Client? {
        return listClients().firstOrNull { it.id == id && it.public }
    }

    /**
     * Return the [Client] identified by [clientId] if the [clientSecret] matches the one configured.
     * Otherwise, return null whether a credential is missing, no client matches or the secret does not match.
     *
     * This method is reserved for confidential (private) clients: since it returns null when no [clientSecret] is
     * provided, it cannot authenticate public clients. Use [findPublicClientByIdOrNull] to resolve a public client.
     */
    suspend fun authenticateClientOrNull(clientId: String?, clientSecret: String?): Client? {
        if (clientId == null || clientSecret == null) {
            return null
        }
        return listClients().firstOrNull { it.id == clientId && it.secret == clientSecret }
    }
}
