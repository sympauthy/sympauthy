package com.sympauthy.business.model.oauth2

import com.sympauthy.business.model.user.isOpenIdConnectScope

/**
 * Represents a scope that can be requested during an OAuth2/OpenID Connect flow.
 *
 * A scope has two shapes: an [EnabledScope] this server honours, and a [DisabledScope] the
 * deployment turned off. A disabled one is listed by the administration API and nothing else: it is
 * unknown to every protocol exchange, so it can neither be requested, consented to, granted, nor
 * put in a token.
 *
 * Equality is based solely on the [scope] string identifier, so that scopes can be compared
 * and stored in sets regardless of their type.
 */
sealed class Scope(
    val scope: String,
    /**
     * What the scope is for, and therefore how it is obtained. A disabled scope carries it too:
     * turning a scope off does not change what it would be.
     */
    val type: ScopeType,
    /**
     * Identifier of the audience this scope is restricted to.
     * When null, the scope is shared across all audiences.
     */
    val audienceId: String? = null
) {
    override fun equals(other: Any?) = other is Scope && scope == other.scope
    override fun hashCode() = scope.hashCode()
    override fun toString() = scope
}

/**
 * A scope this authorization server serves.
 *
 * Everything a scope can take part in — a consent, a granting rule, a token — takes one of these
 * rather than a [Scope], which is what keeps a scope the deployment turned off out of an exchange
 * without anything having to test for it.
 *
 * The three subclasses are the three [ScopeType]:
 * - [ConsentableUserScope]: scopes that require user consent (e.g., `profile`, `email`).
 * - [GrantableUserScope]: scopes that are granted through granting rules or auto-granted (e.g., `openid`, admin scopes).
 * - [ClientScope]: scopes that are only usable in `client_credentials` flows.
 */
sealed class EnabledScope(
    scope: String,
    type: ScopeType,
    /**
     * Whether the scope is advertised by the OpenID Connect discovery document.
     */
    val discoverable: Boolean,
    audienceId: String? = null
) : Scope(scope, type, audienceId)

/**
 * A scope that requires user consent to be included in tokens.
 * These scopes come from user consent (e.g., `profile`, `email`, `address`, `phone`)
 * and are never granted through granting rules.
 */
class ConsentableUserScope(
    scope: String,
    discoverable: Boolean = true,
    audienceId: String? = null
) : EnabledScope(scope, ScopeType.CONSENTABLE, discoverable, audienceId)

/**
 * A scope that is granted through granting rules or auto-granted.
 * These scopes (e.g., `openid`, admin scopes) never require user consent.
 */
class GrantableUserScope(
    scope: String,
    discoverable: Boolean,
    audienceId: String? = null
) : EnabledScope(scope, ScopeType.GRANTABLE, discoverable, audienceId)

/**
 * A scope that is only usable in `client_credentials` flows.
 * These scopes are never discoverable and are not tied to user consent or granting rules.
 */
class ClientScope(
    scope: String
) : EnabledScope(scope, ScopeType.CLIENT, discoverable = false)

/**
 * A scope the deployment turned off, which this server lists and never serves.
 *
 * It carries no reason for being off: there is exactly one, that the deployment wrote
 * `enabled: false` against it.
 *
 * It is not discoverable either, and that is not a property it holds: discovery advertises what a
 * client may request, and a disabled scope is not something a client may request.
 */
class DisabledScope(
    scope: String,
    type: ScopeType,
    audienceId: String? = null
) : Scope(scope, type, audienceId)

/**
 * True if this scope is an admin scope granting access to administration APIs.
 */
val Scope.isAdmin: Boolean get() = type == ScopeType.GRANTABLE && scope.isAdminScope()

/**
 * True if this scope is a user scope (either consentable or grantable).
 */
val Scope.isUserScope: Boolean get() = type == ScopeType.CONSENTABLE || type == ScopeType.GRANTABLE

/**
 * True if this scope is a client scope for `client_credentials` flows.
 */
val Scope.isClientScope: Boolean get() = type == ScopeType.CLIENT

/**
 * True if this authorization server serves this scope.
 */
val Scope.isEnabled: Boolean get() = this is EnabledScope

/**
 * What a scope is for, which decides how a caller comes to hold it.
 */
enum class ScopeType {
    /** Scope an end-user consents to. */
    CONSENTABLE,

    /** Scope granted by a scope granting rule, or auto-granted. */
    GRANTABLE,

    /** Scope a client obtains for itself in a `client_credentials` flow. */
    CLIENT
}

/**
 * Origin of a scope, indicating which specification or system defines it.
 */
enum class ScopeOrigin {
    /** Scope defined by the OpenID Connect specification. */
    OPENID,

    /** Scope defined by SympAuthy for administration or client APIs. */
    SYSTEM,

    /** Scope defined by the operator in configuration. */
    CUSTOM
}

/**
 * The origin of this scope, indicating where it is defined.
 */
val Scope.origin: ScopeOrigin
    get() = when {
        scope.isOpenIdConnectScope() || scope.isBuiltInGrantableScope() -> ScopeOrigin.OPENID
        scope.isAdminScope() || scope.isBuiltInClientScope() -> ScopeOrigin.SYSTEM
        else -> ScopeOrigin.CUSTOM
    }
