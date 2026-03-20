package com.sympauthy.business.model.user

/**
 * Enumeration of scopes defined in the OpenID Connect specification that are supported by this application.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims">Scope</a>
 */
enum class OpenIdConnectScope(
    val scope: String
) {
    PROFILE(OpenIdConnectScopeId.PROFILE),
    EMAIL(OpenIdConnectScopeId.EMAIL),
    ADDRESS(OpenIdConnectScopeId.ADDRESS),
    PHONE(OpenIdConnectScopeId.PHONE);
}

/**
 * Return true if the scope id belongs to the [OpenIdConnectScope] defined in the OpenID Connect specification.
 */
fun String.isOpenIdConnectScope(): Boolean = OpenIdConnectScope.entries.any { it.scope == this }

object OpenIdConnectScopeId {
    const val PROFILE = "profile"
    const val EMAIL = "email"
    const val ADDRESS = "address"
    const val PHONE = "phone"
}
