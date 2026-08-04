package com.sympauthy.business.model.oauth2

/**
 * Enumeration of built-in scopes usable only in `client_credentials` flows.
 *
 * These scopes are always available and cannot be configured or disabled via YAML configuration.
 * They allow a client to access user-related APIs on behalf of itself (not a specific user).
 *
 * @see BuiltInClientScopeId for the string constants used in configuration and token claims.
 */
enum class BuiltInClientScope(
    val scope: String
) {
    USERS_READ(BuiltInClientScopeId.USERS_READ),
    USERS_CLAIMS_READ(BuiltInClientScopeId.USERS_CLAIMS_READ),
    USERS_CLAIMS_WRITE(BuiltInClientScopeId.USERS_CLAIMS_WRITE),
    USERS_MFA_READ(BuiltInClientScopeId.USERS_MFA_READ),
    USERS_MFA_WRITE(BuiltInClientScopeId.USERS_MFA_WRITE),
    USERS_PROVIDERS_WRITE(BuiltInClientScopeId.USERS_PROVIDERS_WRITE),
    INVITATIONS_READ(BuiltInClientScopeId.INVITATIONS_READ),
    INVITATIONS_WRITE(BuiltInClientScopeId.INVITATIONS_WRITE);
}

/**
 * Return true if the scope id belongs to a [BuiltInClientScope].
 */
fun String.isBuiltInClientScope(): Boolean = BuiltInClientScope.entries.any { it.scope == this }

object BuiltInClientScopeId {
    const val USERS_READ = "users:read"
    const val USERS_CLAIMS_READ = "users:claims:read"
    const val USERS_CLAIMS_WRITE = "users:claims:write"
    const val USERS_MFA_READ = "users:mfa:read"
    const val USERS_MFA_WRITE = "users:mfa:write"
    const val USERS_PROVIDERS_WRITE = "users:providers:write"
    const val INVITATIONS_READ = "invitations:read"
    const val INVITATIONS_WRITE = "invitations:write"
}
