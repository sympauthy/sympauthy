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
 * - `consent`: manage consents.
 * - `grants`: read the grants tokens are issued under, and revoke them, one at a time or every one of a person's
 *   at once.
 * - `interactive-flow-sessions`: read the interactive flow sessions currently in flight.
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
    CONSENT_WRITE(AdminScopeId.CONSENT_WRITE),
    INVITATIONS_READ(AdminScopeId.INVITATIONS_READ),
    INVITATIONS_WRITE(AdminScopeId.INVITATIONS_WRITE),
    GRANTS_READ(AdminScopeId.GRANTS_READ),
    GRANTS_WRITE(AdminScopeId.GRANTS_WRITE),
    INTERACTIVE_FLOW_SESSIONS_READ(AdminScopeId.INTERACTIVE_FLOW_SESSIONS_READ);
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
    const val INVITATIONS_READ = "admin:invitations:read"
    const val INVITATIONS_WRITE = "admin:invitations:write"
    const val GRANTS_READ = "admin:grants:read"
    const val GRANTS_WRITE = "admin:grants:write"

    /**
     * Not `admin:sessions:read`: a session on an admin surface reads as the logged-in sessions and tokens a
     * console lists and revokes, which is a different surface nobody has built yet and the one that would
     * naturally want the short name. Nor `admin:flows:read`, a flow being configuration and already under
     * [CONFIG_READ]. Renaming a path later is a version; renaming a scope is something every deployment has
     * to re-grant.
     */
    const val INTERACTIVE_FLOW_SESSIONS_READ = "admin:interactive-flow-sessions:read"
}
