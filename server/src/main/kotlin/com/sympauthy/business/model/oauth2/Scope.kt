package com.sympauthy.business.model.oauth2

import com.sympauthy.business.model.user.isOpenIdConnectScope

/**
 * A scope this authorization server knows about, whether or not it serves it.
 *
 * A scope has two shapes: an [EnabledScope], which the server honours in an OAuth2/OpenID Connect
 * exchange, and a [DisabledScope], which the deployment turned off. Everything that resolves,
 * grants, consents to or issues a scope takes an [EnabledScope], so a scope that is off cannot
 * reach a token; the administration API is the only thing that reads the two together.
 *
 * Equality is based solely on the [scope] string identifier, so that scopes can be compared
 * and stored in sets regardless of their type.
 */
sealed class Scope(
    val scope: String,
    /**
     * Identifier of the audience this scope is restricted to.
     * When null, the scope is shared across all audiences.
     */
    val audienceId: String? = null
) {
    /**
     * What the scope may be used for. A scope keeps it once it is turned off, because that is what
     * it would be again.
     */
    abstract val type: ScopeType

    override fun equals(other: Any?) = other is Scope && scope == other.scope
    override fun hashCode() = scope.hashCode()
    override fun toString() = scope
}

/**
 * A scope this authorization server serves.
 *
 * Enabled scopes are divided into three types:
 * - [ConsentableUserScope]: scopes that require user consent (e.g., `profile`, `email`).
 * - [GrantableUserScope]: scopes that are granted through granting rules or auto-granted (e.g., `openid`, admin scopes).
 * - [ClientScope]: scopes that are only usable in `client_credentials` flows.
 */
sealed class EnabledScope(
    scope: String,
    val discoverable: Boolean,
    audienceId: String? = null
) : Scope(scope, audienceId)

/**
 * A scope that requires user consent to be included in tokens.
 * These scopes come from user consent (e.g., `profile`, `email`, `address`, `phone`)
 * and are never granted through granting rules.
 */
class ConsentableUserScope(
    scope: String,
    discoverable: Boolean = true,
    audienceId: String? = null
) : EnabledScope(scope, discoverable, audienceId) {
    override val type = ScopeType.CONSENTABLE
}

/**
 * A scope that is granted through granting rules or auto-granted.
 * These scopes (e.g., `openid`, admin scopes) never require user consent.
 */
class GrantableUserScope(
    scope: String,
    discoverable: Boolean,
    audienceId: String? = null
) : EnabledScope(scope, discoverable, audienceId) {
    override val type = ScopeType.GRANTABLE
}

/**
 * A scope that is only usable in `client_credentials` flows.
 * These scopes are never discoverable and are not tied to user consent or granting rules.
 */
class ClientScope(
    scope: String
) : EnabledScope(scope, discoverable = false) {
    override val type = ScopeType.CLIENT
}

/**
 * A scope the deployment turned off.
 *
 * It exists to be listed to an administrator and to nothing else: no token request resolves it, no
 * client may allow it and no claim may be protected by it. Only a scope a deployment configures can
 * become one, so the scopes the server defines itself are never disabled.
 */
class DisabledScope(
    scope: String,
    override val type: ScopeType,
    audienceId: String? = null
) : Scope(scope, audienceId)

/**
 * What a scope may be used for, independently of whether the server serves it.
 *
 * [value] is the spelling a deployment writes in its configuration and the one the administration
 * API publishes.
 */
enum class ScopeType(val value: String) {
    /** Included in a token once the end-user consents to it. */
    CONSENTABLE("consentable"),

    /** Granted by a rule or auto-granted, never consented to. */
    GRANTABLE("grantable"),

    /** Only usable in a `client_credentials` flow. */
    CLIENT("client")
}

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

/**
 * True if this authorization server serves this scope.
 */
val Scope.isEnabled: Boolean get() = this is EnabledScope

/**
 * Origin of a scope, indicating which specification or system defines it.
 */
enum class ScopeOrigin(val value: String) {
    /** Scope defined by the OpenID Connect specification. */
    OPENID("openid"),

    /** Scope defined by SympAuthy for administration or client APIs. */
    SYSTEM("system"),

    /** Scope defined by the operator in configuration. */
    CUSTOM("custom")
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
