package com.sympauthy.business.model.security

/**
 * Where one request came from, as this server observed it under the trust model a deployment
 * configured — which `docs/security-context.md` carries.
 *
 * It is computed once per request at the boundary, by `ObservedRequestFilter`, and reaches whatever
 * needs it as an ordinary parameter rather than as something a manager reaches back for.
 *
 * It is named for the request rather than for the record it is written to, because a throttle reads one
 * without storing anything and a security context is not what that is about.
 */
data class ObservedRequest(
    /**
     * The address the request is attributed to.
     *
     * Never null: where nothing answered for it — no edge named, the named edge's header absent, a
     * configuration that did not parse — this is the peer of the socket the request arrived on, which
     * is the proxy's own address rather than nothing at all. [ipSource] says which of those happened.
     */
    val ipAddress: String,
    /**
     * Which of the three answers [ipAddress] came from, so a consumer deciding what it is worth does
     * not read the configuration a second time and reach a different verdict from this one.
     */
    val ipSource: IpSource,
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
