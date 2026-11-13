package com.sympauthy.business.manager

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.client.ClientReference
import com.sympauthy.business.model.collection.CollectionFieldValue
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
     * Return the [ClientReference] a record naming the client [id] is published with, whether the configuration
     * still declares that client or not.
     */
    suspend fun getClientReference(id: String): ClientReference {
        return ClientReference(id = id, displayName = findClientByIdOrNull(id)?.displayName)
    }

    /**
     * Return the [ClientReference] of each of [ids], keyed by identifier, reading the configuration once for
     * all of them.
     */
    suspend fun getClientReferences(ids: Collection<String>): Map<String, ClientReference> {
        val displayNames = listClients().associate { it.id to it.displayName }
        return ids.associateWith { ClientReference(id = it, displayName = displayNames[it]) }
    }

    /**
     * Return the values of a collection field over clients: every client the configuration declares, then each
     * of [storedIds] it no longer declares, so a row naming one is still one a caller can filter on.
     *
     * Each value is named under `fields.client_id` and its identifier, and read under the display name the
     * configuration gives it where no bundle names it, or under its identifier where it has none.
     */
    suspend fun getClientFieldValues(storedIds: Collection<String>): List<CollectionFieldValue> {
        val clients = listClients()
        val declaredIds = clients.map(Client::id).toSet()
        val references = clients.map { ClientReference(id = it.id, displayName = it.displayName) } +
            storedIds.distinct().filterNot(declaredIds::contains).map { ClientReference(id = it, displayName = null) }
        return references.map {
            CollectionFieldValue(it.id, "$CLIENT_FIELD_KEY.${it.id}", it.displayName ?: it.id)
        }
    }

    /**
     * Return the [Client] identified by [id] or throw a non-recoverable business exception
     * ```client.invalid_client_id```.
     */
    suspend fun findClientById(id: String): Client {
        return findClientByIdOrNull(id) ?: throw businessExceptionOf(
            detailsId = "client.invalid_client_id",
            descriptionId = "description.client.invalid_client_id",
            values = arrayOf("clientId" to id)
        )
    }

    /**
     * Parse the [uncheckedClientId] and return the corresponding [Client] or throw a non-recoverable business
     * exception.
     */
    suspend fun parseRequestedClient(uncheckedClientId: String?): Client {
        if (uncheckedClientId.isNullOrBlank()) {
            throw businessExceptionOf(
                detailsId = "client.parse_requested.missing",
                descriptionId = "description.client.parse_requested.missing"
            )
        }
        return findClientByIdOrNull(uncheckedClientId) ?: throw businessExceptionOf(
            detailsId = "client.parse_requested.invalid_client_id",
            descriptionId = "description.client.parse_requested.invalid_client_id",
            values = arrayOf("clientId" to uncheckedClientId)
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

/**
 * The display key every collection field over clients is named under.
 */
const val CLIENT_FIELD_KEY = "fields.client_id"
