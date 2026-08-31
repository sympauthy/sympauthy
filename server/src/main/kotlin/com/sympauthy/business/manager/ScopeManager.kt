package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.client.Client
import com.sympauthy.business.model.oauth2.*
import com.sympauthy.business.model.user.claim.Claim
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.model.orThrow
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ScopeManager(
    @Inject private val uncheckedScopesConfig: ScopesConfig,
    @Inject private val claimManager: ClaimManager
) {

    /**
     * List of every [EnabledScope] this authorization server serves.
     */
    suspend fun listScopes(): List<EnabledScope> {
        return uncheckedScopesConfig.orThrow().scopes
    }

    /**
     * List all [EnabledScope] visible for the given [audienceId].
     * A scope is visible if it has no audience restriction or is restricted to this audience.
     */
    suspend fun listScopesForAudience(audienceId: String): List<EnabledScope> {
        return listScopes().filter { it.audienceId == null || it.audienceId == audienceId }
    }

    /**
     * Return the [EnabledScope], otherwise null, if:
     * - [scope] is an OpenID Connect scope and has not been explicitly disabled by configuration.
     * - [scope] is a custom scope and has been properly defined in the configuration.
     */
    suspend fun find(scope: String): EnabledScope? {
        return listScopes().firstOrNull { it.scope == scope }
    }

    /**
     * Return the [EnabledScope] if:
     * - [scope] is an OpenID Connect scope and has not been explicitly disabled by configuration.
     * - [scope] is a custom scope and has been properly defined in the configuration.
     * Otherwise, throws an unrecoverable "scope.unsupported" exception.
     */
    suspend fun findOrThrow(scope: String): EnabledScope {
        return find(scope) ?: throw businessExceptionOf(
            detailsId = "scope.unsupported",
            values = arrayOf("scope" to scope)
        )
    }

    /**
     * Return the [EnabledScope] if [scope] is a scope that exists and is allowed by the [client] in [Client.allowedScopes].
     * Otherwise, throws an unrecoverable "scope.unsupported" exception.
     */
    suspend fun findForClientOrThrow(client: Client, scope: String): EnabledScope {
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
    fun listClaimsProtectedByScope(scope: EnabledScope): List<Claim> {
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
    ): List<EnabledScope> {
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
