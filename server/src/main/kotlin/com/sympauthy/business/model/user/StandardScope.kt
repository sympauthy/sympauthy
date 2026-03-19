package com.sympauthy.business.model.user

/**
 * Enumeration of scope, defined in the OpenID specification, that are supported by this application.
 *
 * TODO: Add support for custom scope
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims">Scope</a>
 */
enum class StandardScope(
    val scope: String
) {
    PROFILE(StandardScopeId.PROFILE),
    EMAIL(StandardScopeId.EMAIL),
    ADDRESS(StandardScopeId.ADDRESS),
    PHONE(StandardScopeId.PHONE);
}

/**
 * Return true if the scope id belongs to the [StandardScope] defined in the OpenID specification.
 */
fun String.isStandardScope(): Boolean = StandardScope.entries.any { it.scope == this }

object StandardScopeId {
    const val PROFILE = "profile"
    const val EMAIL = "email"
    const val ADDRESS = "address"
    const val PHONE = "phone"
}
