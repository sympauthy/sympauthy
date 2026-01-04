package com.sympauthy.business.manager

import com.sympauthy.business.exception.BusinessException
import com.sympauthy.business.exception.businessExceptionOf
import com.sympauthy.business.model.oauth2.Scope
import com.sympauthy.business.model.user.StandardScope
import com.sympauthy.config.model.AuthConfig
import com.sympauthy.config.model.ScopesConfig
import com.sympauthy.config.model.StandardScopeConfig
import com.sympauthy.config.model.orThrow
import com.sympauthy.exception.LocalizedException
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

@Singleton
class ScopeManager(
    @Inject private val uncheckedAuthConfig: AuthConfig,
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
     * Custom scope allowing the user to access the administration APIs of this authorization server.
     */
    val adminScope: Scope
        get() {
            val adminScope = UriBuilder.of(uncheckedAuthConfig.orThrow().issuer)
                .path("admin")
                .build()
            return Scope(
                scope = adminScope.toASCIIString(),
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
     * Parse the request scope provided to the authorization endpoint into a list of known [Scope].
     * Throws a [LocalizedException] if one of the request scope cannot be found according to the rules described
     * on [findOrThrow].
     */
    suspend fun parseRequestScope(requestScope: String?): List<Scope>? {
        return requestScope?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.map { findOrThrow(it) }
    }

    /**
     * List of [Scope] enabled on this authorization server.
     *
     * The list contains both standard claims defined in the OpenID specification and custom scopes defined by
     * the operator of this authorization server.
     */
    suspend fun listScopes(): List<Scope> {
        return listOf(adminScope) + enabledStandardScopes.toList()
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
     *
     * Otherwise, throws a "scope.unsupported" error as a [BusinessException].
     */
    suspend fun findOrThrow(scope: String): Scope {
        return find(scope) ?: throw businessExceptionOf(
            detailsId = "scope.unsupported",
            values = arrayOf("scope" to scope)
        )
    }
}
