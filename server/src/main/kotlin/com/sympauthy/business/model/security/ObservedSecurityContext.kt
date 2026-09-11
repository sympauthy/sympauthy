package com.sympauthy.business.model.security

/**
 * Where one request came from, as this server observed it under
 * [the trust model][EdgeProvider] a deployment configured.
 *
 * It is what a caller holding a request extracts and passes on as an ordinary parameter, rather than
 * something a manager reaches back for: `docs/general-code-standard.md` keeps a manager callable
 * from a scheduled job, a repair script and a unit test, and every manager here is `suspend`, so a
 * thread-local request context would be intermittently absent across the coroutine boundaries they
 * cross.
 *
 * Nothing stores one yet.
 */
data class ObservedSecurityContext(
    /**
     * The address the request is attributed to.
     *
     * Never null: where no edge is configured, where the configured edge sent no header, and where
     * an override named a header that did not arrive, this is the peer of the socket the request
     * arrived on — which is the proxy's own address rather than nothing at all.
     */
    val ipAddress: String,
    /**
     * The `User-Agent` the request carried, which is the caller's to write and is recorded as what
     * the caller claimed rather than as anything established.
     */
    val userAgent: String?,
    /**
     * Where the deployment's edge placed [ipAddress], or null where no edge supplied any of it.
     */
    val geo: SecurityContextGeo?
)
