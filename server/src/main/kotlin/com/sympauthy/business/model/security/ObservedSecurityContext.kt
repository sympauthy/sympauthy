package com.sympauthy.business.model.security

/**
 * Where one request came from, as this server observed it under the trust model a deployment
 * configured — which `docs/security.md` carries.
 *
 * It is what a caller holding a request extracts and passes on as an ordinary parameter, rather than
 * something a manager reaches back for.
 *
 * Nothing stores one yet.
 */
data class ObservedSecurityContext(
    /**
     * The address the request is attributed to.
     *
     * Never null: where nothing answered for it — no edge named, the named edge's header absent, an
     * override naming a header that did not arrive — this is the peer of the socket the request
     * arrived on, which is the proxy's own address rather than nothing at all.
     */
    val ipAddress: String,
    /**
     * The `User-Agent` the request carried, which is the caller's to write and is recorded as what
     * the caller claimed rather than as anything established.
     */
    val userAgent: String?,
    /**
     * Where the deployment's edges placed [ipAddress], or null where none supplied any of it.
     */
    val geo: SecurityContextGeo?
)
