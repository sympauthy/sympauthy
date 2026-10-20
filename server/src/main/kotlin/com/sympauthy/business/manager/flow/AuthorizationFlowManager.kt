package com.sympauthy.business.manager.flow

import com.sympauthy.business.model.flow.AuthorizationFlow
import com.sympauthy.business.model.flow.InteractiveFlow
import com.sympauthy.config.model.AuthorizationFlowsConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Manager providing methods shared between all types of end-user authorization flows.
 * This does not handle client authentication (e.g. client_credentials).
 */
@Singleton
class AuthorizationFlowManager(
    @Inject private val authorizationFlowsConfig: AuthorizationFlowsConfig,
) {

    /**
     * The interactive flow bundled with this authorization server.
     */
    val defaultInteractiveFlow: InteractiveFlow
        get() = authorizationFlowsConfig.orThrow().bundledFlow

    /**
     * Return the [AuthorizationFlow] identified by [id] or null.
     */
    fun findByIdOrNull(id: String): AuthorizationFlow? {
        return authorizationFlowsConfig.orThrow().flows
            .firstOrNull { it.id == id }
    }
}
