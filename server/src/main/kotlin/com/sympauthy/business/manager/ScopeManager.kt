package com.sympauthy.business.manager

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.AdminScope
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.StandardScope
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.model.StandardScopeConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

@Singleton
class ScopeManager(
    @Inject private val uncheckedScopesConfig: ScopesConfig
) {
    /**
     * List of scopes defined in the OAuth 2 & OpenId specifications.
     */
    private val enabledStandardScopes: Flow<Scope> = flow {
        StandardScope.entries.forEach { standardScope ->
            val config = uncheckedScopesConfig.orThrow().scopes.asSequence()
                .filterIsInstance<StandardScopeConfig>()
                .firstOrNull { it.scope == standardScope.scope }
            val scope = toScope(config = config, standardScope = standardScope)
            scope?.let { emit(it) }
        }
    }.buffer()

    /**
     * Built-in scopes granting access to the administration APIs of this authorization server.
     */
    val adminScopes: List<Scope> = AdminScope.entries.map { adminScope ->
        Scope(
            scope = adminScope.scope,
            admin = true,
            discoverable = false
        )
    }

    /**
     * Convert a [standardScope] into a [Scope].
     * Return null if the scope has been disabled by the [config].
     */
    private fun toScope(
        config: StandardScopeConfig?,
        standardScope: StandardScope
    ): Scope? {
        if (config != null && !config.enabled) {
            return null
        }
        return Scope(
            scope = standardScope.scope,
            admin = false,
            discoverable = true
        )
    }

    /**
     * List of [Scope] enabled on this authorization server.
     *
     * The list contains both standard claims defined in the OpenID specification and custom scopes defined by
     * the operator of this authorization server.
     */
    suspend fun listScopes(): List<Scope> {
        return adminScopes + enabledStandardScopes.toList()
    }

    /**
     * Return the [Scope], otherwise null, if:
     * - [scope] is a standard scope and its has not been explicitly disabled by configuration.
     * - [scope] is a custom scope and have been properly defined in the configuration.
     */
    suspend fun find(scope: String): Scope? {
        return listScopes().firstOrNull { it.scope == scope }
    }

    /**
     * Return the [Scope] if:
     * - [scope] is a standard scope and its has not been explicitly disabled by configuration.
     * - [scope] is a custom scope and have been properly defined in the configuration.
     * Otherwise, throws an unrecoverable "scope.unsupported" exception.
     */
    suspend fun findOrThrow(scope: String): Scope {
        return find(scope) ?: throw businessExceptionOf(
            detailsId = "scope.unsupported",
            values = arrayOf("scope" to scope)
        )
    }

    /**
     * Return the [Scope] if [scope] is a scope that exists and is allowed by the [client] in [Client.allowedScopes].
     * Otherwise, throws an unrecoverable "scope.unsupported" exception.
     */
    suspend fun findForClientOrThrow(client: Client, scope: String): Scope {
        val foundScope = findOrThrow(scope)

        // If client has allowedScopes defined, check if the scope is in the allowed list
        if (client.allowedScopes != null && !client.allowedScopes.contains(foundScope)) {
            throw businessExceptionOf(
                detailsId = "scope.unsupported",
                values = arrayOf("scope" to scope)
            )
        }

        return foundScope
    }

    /**
     * Parses and processes the scopes requested by the end-user.
     *
     * This method does the following:
     * - If no scope is provided by the end-user, return the default scopes defined by the [client].
     * - parse the [uncheckedScopes] and throw an unrecoverable exception if it fails.
     * - filter out the scopes that have been disabled by the [client].
     */
    suspend fun parseRequestedScopes(
        client: Client,
        uncheckedScopes: String?
    ): List<Scope> {
        return if (uncheckedScopes.isNullOrBlank()) {
            client.defaultScopes ?: emptyList()
        } else {
            uncheckedScopes.split(" ")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map {
                    findForClientOrThrow(
                        client = client,
                        scope = it
                    )
                }
        }
    }
}
