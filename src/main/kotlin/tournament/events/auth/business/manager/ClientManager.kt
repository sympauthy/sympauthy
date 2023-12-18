package tournament.events.auth.business.manager

import jakarta.inject.Inject
import jakarta.inject.Singleton
import tournament.events.auth.business.model.client.Client

@Singleton
class ClientManager(
    @Inject private val clients: List<Client>
) {

    /**
     * Return the [Client] identified by [clientId] if the [clientSecret] matches the one configured.
     * Otherwise, return null whether no client matches or the secret does not match.
     */
    fun authenticateClient(clientId: String, clientSecret: String): Client? {
        return clients.firstOrNull { it.id == clientId && it.secret == clientSecret }
    }
}
