package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.user.OpenIdConnectScope
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.config.model.*
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

@Singleton
class ScopeManager(
    @Inject private val uncheckedScopesConfig: ScopesConfig,
    @Inject private val uncheckedAdminConfig: AdminConfig,
    @Inject private val claimManager: ClaimManager
) {
    /**
     * Consentable scopes defined in the OpenID Connect specification (profile, email, address, phone).
     */
    private val enabledOpenIdConnectScopes: Flow<Scope> = flow {
        OpenIdConnectScope.entries.forEach { standardScope ->
            val config = uncheckedScopesConfig.orThrow().scopes.asSequence()
                .filterIsInstance<OpenIdConnectScopeConfig>()
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
                    ConsentableUserScope(scope = config.scope, discoverable = true, audienceId = config.audienceId)
                } else {
                    GrantableUserScope(scope = config.scope, discoverable = true, audienceId = config.audienceId)
                }
                emit(scope)
            }
    }.buffer()

    /**
     * Built-in scopes granting access to the administration APIs of this authorization server.
     * When admin is enabled, scopes are bound to the configured admin audience.
     * When admin is disabled, returns an empty list.
     */
    val adminScopes: List<Scope> by lazy {
        when (val config = uncheckedAdminConfig) {
            is EnabledAdminConfig -> AdminScope.entries.map { adminScope ->
                GrantableUserScope(
                    scope = adminScope.scope,
                    discoverable = false,
                    audienceId = config.audienceId
                )
            }
            is DisabledAdminConfig -> emptyList()
        }
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
     * Convert an [OpenIdConnectScope] into a [ConsentableUserScope].
     * Return null if the scope has been disabled by the [config].
     */
    private fun toScope(
        config: OpenIdConnectScopeConfig?,
        standardScope: OpenIdConnectScope
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
     * OpenID Connect consentable scopes, and custom scopes.
     */
    suspend fun listScopes(): List<Scope> {
        return builtInGrantableScopes + adminScopes + clientScopes +
                enabledOpenIdConnectScopes.toList() + customScopes.toList()
    }

    /**
     * List all [Scope] visible for the given [audienceId].
     * A scope is visible if it has no audience restriction or is restricted to this audience.
     */
    suspend fun listScopesForAudience(audienceId: String): List<Scope> {
        return listScopes().filter { it.audienceId == null || it.audienceId == audienceId }
    }

    /**
     * Return the [Scope], otherwise null, if:
     * - [scope] is an OpenID Connect scope and has not been explicitly disabled by configuration.
     * - [scope] is a custom scope and has been properly defined in the configuration.
     */
    suspend fun find(scope: String): Scope? {
        return listScopes().firstOrNull { it.scope == scope }
    }

    /**
     * Return the [Scope] if:
     * - [scope] is an OpenID Connect scope and has not been explicitly disabled by configuration.
     * - [scope] is a custom scope and has been properly defined in the configuration.
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

        // Validate that the scope's audience matches the client's audience (or scope has no audience)
        if (foundScope.audienceId != null && foundScope.audienceId != client.audience.id) {
            throw businessExceptionOf(
                detailsId = "scope.audience_mismatch",
                values = arrayOf(
                    "scope" to scope,
                    "scopeAudience" to foundScope.audienceId,
                    "clientAudience" to client.audience.id
                )
            )
        }

        // If client has allowedScopes defined, check if the scope is in the allowed list
        if (client.allowedScopes != null && !client.allowedScopes.contains(foundScope)) {
            throw businessExceptionOf(
                detailsId = "scope.not_allowed",
                values = arrayOf("scope" to scope)
            )
        }

        return foundScope
    }

    /**
     * Return the list of [Claim] that are protected by the given [scope].
     * A claim is protected by a scope if the scope must be requested to read the claim.
     *
     * Only consentable scopes protect claims. Returns an empty list for grantable and client scopes.
     */
    fun listClaimsProtectedByScope(scope: Scope): List<Claim> {
        if (scope !is ConsentableUserScope) return emptyList()
        return claimManager.listAllClaims()
            .filter { it.belongsToScope(scope.scope) }
    }

    /**
     * Parses and processes the scopes requested by the end-user.
     * Only returns user scopes (consentable and grantable), not client scopes.
     *
     * This method does the following:
     * - If no scope is provided by the end-user, return the default scopes defined by the [client].
     * - parse the [uncheckedScopes] and throw an unrecoverable exception if it fails.
     * - reject scopes whose audience does not match the [client]'s audience.
     * - reject scopes that are not in the [client]'s allowed scopes.
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
                .map { scope ->
                    val foundScope = find(scope) ?: throw BusinessException(
                        recoverable = false,
                        detailsId = "scope.parse_requested.unsupported",
                        descriptionId = "description.scope.parse_requested.unsupported",
                        values = mapOf("scope" to scope)
                    )
                    if (foundScope.audienceId != null && foundScope.audienceId != client.audience.id) {
                        throw businessExceptionOf(
                            detailsId = "scope.audience_mismatch",
                            values = arrayOf(
                                "scope" to scope,
                                "scopeAudience" to foundScope.audienceId,
                                "clientAudience" to client.audience.id
                            )
                        )
                    }
                    if (client.allowedScopes != null && !client.allowedScopes.contains(foundScope)) {
                        throw BusinessException(
                            recoverable = false,
                            detailsId = "scope.parse_requested.not_allowed",
                            descriptionId = "description.scope.parse_requested.not_allowed",
                            values = mapOf("scope" to scope)
                        )
                    }
                    foundScope
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
