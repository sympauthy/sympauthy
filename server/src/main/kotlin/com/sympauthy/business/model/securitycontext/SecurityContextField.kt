package com.sympauthy.business.model.securitycontext

/**
 * A field of the security context an edge proxy supplies, and the name a deployment binds a header to
 * under `advanced.security-context.headers`.
 *
 * The user agent is not one of them. It is the caller's own `User-Agent` on every deployment, and no
 * proxy republishes it under a name of its own.
 */
enum class SecurityContextField {
    CLIENT_IP,
    COUNTRY,
    REGION,
    CITY
}
