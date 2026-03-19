package com.sympauthy.business.model.oauth2

/**
 * Enumeration of built-in grantable scopes that are not admin scopes.
 *
 * These scopes are always available and cannot be configured or disabled via YAML configuration.
 * Scopes marked as [autoGranted] are automatically granted when requested, without needing
 * a scope granting rule.
 *
 * @see BuiltInGrantableScopeId for the string constants used in configuration and token claims.
 */
enum class BuiltInGrantableScope(
    val scope: String,
    val discoverable: Boolean,
    val autoGranted: Boolean
) {
    OPENID(BuiltInGrantableScopeId.OPENID, discoverable = true, autoGranted = true);
}

/**
 * Return true if the scope id belongs to a [BuiltInGrantableScope].
 */
fun String.isBuiltInGrantableScope(): Boolean = BuiltInGrantableScope.entries.any { it.scope == this }

object BuiltInGrantableScopeId {
    const val OPENID = "openid"
}
