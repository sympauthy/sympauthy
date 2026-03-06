package com.sympauthy.business.model.oauth2

/**
 * Enumeration of built-in scopes granting access to the administration APIs of this authorization server.
 *
 * These scopes are always available and cannot be configured or disabled via YAML configuration.
 * They are granted to end-users through scope granting rules during the authorization code flow.
 *
 * Each scope controls access to a specific area of the admin API:
 * - `clients`: manage OAuth2 clients registered on this server.
 * - `users`: manage end-users (read, write, delete).
 * - `access`: manage access tokens and authorizations.
 * - `sessions`: manage user sessions.
 *
 * @see AdminScopeId for the string constants used in configuration and token claims.
 */
enum class AdminScope(
    val scope: String
) {
    CLIENTS_READ(AdminScopeId.CLIENTS_READ),
    USERS_READ(AdminScopeId.USERS_READ),
    USERS_WRITE(AdminScopeId.USERS_WRITE),
    USERS_DELETE(AdminScopeId.USERS_DELETE),
    ACCESS_READ(AdminScopeId.ACCESS_READ),
    ACCESS_WRITE(AdminScopeId.ACCESS_WRITE),
    SESSIONS_READ(AdminScopeId.SESSIONS_READ),
    SESSIONS_WRITE(AdminScopeId.SESSIONS_WRITE);
}

/**
 * Return true if the scope id belongs to the [AdminScope] for administration APIs.
 */
fun String.isAdminScope(): Boolean = AdminScope.entries.any { it.scope == this }

object AdminScopeId {
    const val CLIENTS_READ = "admin:clients:read"
    const val USERS_READ = "admin:users:read"
    const val USERS_WRITE = "admin:users:write"
    const val USERS_DELETE = "admin:users:delete"
    const val ACCESS_READ = "admin:access:read"
    const val ACCESS_WRITE = "admin:access:write"
    const val SESSIONS_READ = "admin:sessions:read"
    const val SESSIONS_WRITE = "admin:sessions:write"
}
