package com.sympauthy.business.manager

import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.user.StandardScope
import com.sympauthy.config.model.CustomScopeConfig
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
     * Consentable scopes defined in the OpenID specification (profile, email, address, phone).
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
     * Custom scopes defined in configuration, typed as consentable or grantable.
     */
    private val customScopes: Flow<Scope> = flow {
        uncheckedScopesConfig.orThrow().scopes
            .filterIsInstance<CustomScopeConfig>()
            .forEach { config ->
                val scope = if (config.consentable) {
                    ConsentableUserScope(scope = config.scope, discoverable = true)
                } else {
                    GrantableUserScope(scope = config.scope, discoverable = true)
                }
                emit(scope)
            }
    }.buffer()

    /**
     * Built-in scopes granting access to the administration APIs of this authorization server.
     */
    val adminScopes: List<Scope> = AdminScope.entries.map { adminScope ->
        GrantableUserScope(
            scope = adminScope.scope,
            discoverable = false
        )
    }

    /**
     * Built-in grantable scopes (e.g., openid) that are not admin scopes.
     */
    val builtInGrantableScopes: List<Scope> = BuiltInGrantableScope.entries.map { builtIn ->
        GrantableUserScope(
            scope = builtIn.scope,
            discoverable = builtIn.discoverable
        )
    }

    /**
     * Built-in client scopes for `client_credentials` flows.
     */
    val clientScopes: List<Scope> = BuiltInClientScope.entries.map { builtIn ->
        ClientScope(scope = builtIn.scope)
    }

    /**
     * Convert a [standardScope] into a [ConsentableUserScope].
     * Return null if the scope has been disabled by the [config].
     */
    private fun toScope(
        config: StandardScopeConfig?,
        standardScope: StandardScope
    ): Scope? {
        if (config != null && !config.enabled) {
            return null
        }
        return ConsentableUserScope(
            scope = standardScope.scope,
            discoverable = true
        )
    }

    /**
     * List of all [Scope] enabled on this authorization server.
     *
     * Includes built-in grantable scopes, admin scopes, client scopes,
     * standard consentable scopes, and custom scopes.
     */
    suspend fun listScopes(): List<Scope> {
        return builtInGrantableScopes + adminScopes + clientScopes +
            enabledStandardScopes.toList() + customScopes.toList()
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
     * Only returns user scopes (consentable and grantable), not client scopes.
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

    /**
     * Parses and validates the scopes requested in a `client_credentials` flow.
     * Only returns [ClientScope] instances. Throws if any requested scope is not a client scope.
     */
    suspend fun parseRequestedClientScopes(
        client: Client,
        uncheckedScopes: String?
    ): List<ClientScope> {
        if (uncheckedScopes.isNullOrBlank()) {
            return emptyList()
        }
        return uncheckedScopes.split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { scopeStr ->
                val scope = findForClientOrThrow(client, scopeStr)
                scope as? ClientScope ?: throw businessExceptionOf(
                    detailsId = "scope.not_client_scope",
                    values = arrayOf("scope" to scopeStr)
                )
            }
    }
}
