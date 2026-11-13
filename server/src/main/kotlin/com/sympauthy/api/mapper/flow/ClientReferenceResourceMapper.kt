package com.sympauthy.api.mapper.flow

import com.sympauthy.api.resource.flow.ClientReferenceResource
import com.sympauthy.business.manager.ClientManager
import com.sympauthy.business.model.flow.InteractiveFlowSession
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Publishes the client an interactive flow session was started for, which every step names.
 */
@Singleton
class ClientReferenceResourceMapper(
    @Inject private val clientManager: ClientManager
) {

    /**
     * The client [session] was started for, or null where an administrator started it or nothing named one.
     */
    suspend fun toResourceOrNull(session: InteractiveFlowSession): ClientReferenceResource? {
        val client = session.initiatingClientId?.let { clientManager.findClientReference(it) } ?: return null
        return ClientReferenceResource(
            id = client.id,
            displayName = client.displayName
        )
    }
}
