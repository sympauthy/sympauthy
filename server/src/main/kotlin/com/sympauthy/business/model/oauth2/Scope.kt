package com.sympauthy.business.model.oauth2

/**
 * Represents a scope that can be requested during an OAuth2/OpenID Connect flow.
 *
 * Scopes are divided into three types:
 * - [ConsentableUserScope]: scopes that require user consent (e.g., `profile`, `email`).
 * - [GrantableUserScope]: scopes that are granted through granting rules or auto-granted (e.g., `openid`, admin scopes).
 * - [ClientScope]: scopes that are only usable in `client_credentials` flows.
 *
 * Equality is based solely on the [scope] string identifier, so that scopes can be compared
 * and stored in sets regardless of their type.
 */
sealed class Scope(
    val scope: String,
    val discoverable: Boolean
) {
    override fun equals(other: Any?) = other is Scope && scope == other.scope
    override fun hashCode() = scope.hashCode()
    override fun toString() = scope
}

/**
 * A scope that requires user consent to be included in tokens.
 * These scopes come from user consent (e.g., `profile`, `email`, `address`, `phone`)
 * and are never granted through granting rules.
 */
class ConsentableUserScope(
    scope: String,
    discoverable: Boolean = true
) : Scope(scope, discoverable)

/**
 * A scope that is granted through granting rules or auto-granted.
 * These scopes (e.g., `openid`, admin scopes) never require user consent.
 */
class GrantableUserScope(
    scope: String,
    discoverable: Boolean
) : Scope(scope, discoverable)

/**
 * A scope that is only usable in `client_credentials` flows.
 * These scopes are never discoverable and are not tied to user consent or granting rules.
 */
class ClientScope(
    scope: String
) : Scope(scope, discoverable = false)

/**
 * True if this scope is an admin scope granting access to administration APIs.
 */
val Scope.isAdmin: Boolean get() = this is GrantableUserScope && scope.isAdminScope()

/**
 * True if this scope is a user scope (either consentable or grantable).
 */
val Scope.isUserScope: Boolean get() = this is ConsentableUserScope || this is GrantableUserScope

/**
 * True if this scope is a client scope for `client_credentials` flows.
 */
val Scope.isClientScope: Boolean get() = this is ClientScope
