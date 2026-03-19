package com.sympauthy.business.model.oauth2

/**
 * Enumeration of built-in scopes granting access to the administration APIs of this authorization server.
 *
 * These scopes are always available and cannot be configured or disabled via YAML configuration.
 * They are granted to end-users through scope granting rules during the authorization code flow.
 *
 * Each scope controls access to a specific area of the admin API:
 * - `config`: read server configuration (clients, flows, etc.).
 * - `users`: manage end-users (read, write, delete).
 * - `consent`: manage consents and force logout.
 *
 * @see AdminScopeId for the string constants used in configuration and token claims.
 */
enum class AdminScope(
    val scope: String
) {
    CONFIG_READ(AdminScopeId.CONFIG_READ),
    USERS_READ(AdminScopeId.USERS_READ),
    USERS_WRITE(AdminScopeId.USERS_WRITE),
    USERS_DELETE(AdminScopeId.USERS_DELETE),
    CONSENT_READ(AdminScopeId.CONSENT_READ),
    CONSENT_WRITE(AdminScopeId.CONSENT_WRITE);
}

/**
 * Return true if the scope id belongs to the [AdminScope] for administration APIs.
 */
fun String.isAdminScope(): Boolean = AdminScope.entries.any { it.scope == this }

object AdminScopeId {
    const val CONFIG_READ = "admin:config:read"
    const val USERS_READ = "admin:users:read"
    const val USERS_WRITE = "admin:users:write"
    const val USERS_DELETE = "admin:users:delete"
    const val CONSENT_READ = "admin:consent:read"
    const val CONSENT_WRITE = "admin:consent:write"
}
