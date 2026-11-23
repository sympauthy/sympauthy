package com.sympauthy.business.model.scope


/**
 * Represents a scope that can be requested during an OAuth2/OpenID Connect flow: an [EnabledScope] this
 * server honours, or a [DisabledScope] the deployment turned off. See `docs/design/security.md`.
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
 * A scope this authorization server serves, and the only shape of one that a consent, a granting rule or
 * a token takes.
 *
 * The three subclasses are the three [ScopeType]:
 * - [ConsentableUserScope]: scopes that require user consent (e.g., `profile`, `email`).
 * - [GrantableUserScope]: scopes that are granted through granting rules or auto-granted (e.g., `openid`, admin
 *   scopes).
 * - [ClientScope]: scopes that are only usable in `client_credentials` flows.
 */
sealed class EnabledScope(
    scope: String,
    type: ScopeType,
    /**
     * The places this scope is published in, which is the discovery document or nothing at all. A scope is
     * served whether or not it appears there; see `docs/design/security.md`.
     */
    val publishedIn: Set<ScopePublicationPlace>,
    audienceId: String? = null
) : Scope(scope, type, audienceId) {

    /**
     * Return true if this scope is published in [place].
     */
    fun isPublishedIn(place: ScopePublicationPlace): Boolean = place in publishedIn
}

/**
 * A scope that requires user consent to be included in tokens.
 * These scopes come from user consent (e.g., `profile`, `email`, `address`, `phone`)
 * and are never granted through granting rules.
 */
class ConsentableUserScope(
    scope: String,
    publishedIn: Set<ScopePublicationPlace> = setOf(ScopePublicationPlace.DISCOVERY),
    audienceId: String? = null
) : EnabledScope(scope, ScopeType.CONSENTABLE, publishedIn, audienceId)

/**
 * A scope that is granted through granting rules or auto-granted.
 * These scopes (e.g., `openid`, admin scopes) never require user consent.
 */
class GrantableUserScope(
    scope: String,
    publishedIn: Set<ScopePublicationPlace>,
    audienceId: String? = null
) : EnabledScope(scope, ScopeType.GRANTABLE, publishedIn, audienceId)

/**
 * A scope that is only usable in `client_credentials` flows.
 *
 * It is published nowhere, and no deployment decides that; see `docs/design/security.md`.
 */
class ClientScope(
    scope: String
) : EnabledScope(scope, ScopeType.CLIENT, publishedIn = emptySet())

/**
 * A scope the deployment turned off, which this server lists and never serves. It carries no
 * publication places, being published nowhere. See `docs/design/security.md`.
 */
class DisabledScope(
    scope: String,
    type: ScopeType,
    audienceId: String? = null
) : Scope(scope, type, audienceId)

/**
 * True if this scope is an admin scope granting access to administration APIs.
 *
 * Asked of a scope this server serves, because what it answers is what the scope grants: a
 * [DisabledScope] grants nothing, whatever its [Scope.type] says it would be.
 */
val EnabledScope.isAdmin: Boolean get() = this is GrantableUserScope && scope.isAdminScope()

/**
 * True if this scope is a client scope for `client_credentials` flows.
 */
val EnabledScope.isClientScope: Boolean get() = this is ClientScope

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
