package com.sympauthy.security

import com.sympauthy.business.model.oauth2.AuthenticationToken
import com.sympauthy.security.AccessTokenScheme.BEARER
import com.sympauthy.security.AccessTokenScheme.DPOP
import io.micronaut.security.authentication.Authentication

/**
 * The authentication schemes an access token is presented under: as a bearer token, RFC 6750 §2.1, or under
 * the `DPoP` scheme RFC 9449 §7.1 defines for a token bound to a key.
 *
 * Every route accepting an access token accepts it under both, so a challenge names both, in the order they are
 * declared here.
 */
enum class AccessTokenScheme(
    /**
     * The scheme as the `Authorization` header and a `WWW-Authenticate` challenge spell it.
     */
    val scheme: String
) {
    BEARER("Bearer"),
    DPOP("DPoP")
}

/**
 * The scheme the access token behind this authentication was presented under, or null where it was made from
 * anything but an access token.
 *
 * It is read off the token rather than off the request: [AccessTokenValidator] accepts a token bound to a key
 * under [DPOP] alone and refuses an unbound one there, so a token it accepted came under [DPOP] exactly where
 * it is bound, and is a bearer token everywhere else.
 */
val Authentication.accessTokenSchemeOrNull: AccessTokenScheme?
    get() = when (this) {
        is UserAuthentication -> authenticationToken.accessTokenScheme
        is ClientAuthentication -> authenticationToken.accessTokenScheme
        else -> null
    }

/**
 * The scheme this token was presented under, read off the token for the reason [accessTokenSchemeOrNull] gives.
 */
val AuthenticationToken.accessTokenScheme: AccessTokenScheme
    get() = if (dpopJkt != null) DPOP else BEARER
