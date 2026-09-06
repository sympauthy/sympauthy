package com.sympauthy.business.model.securitycontext

/**
 * What one request showed of where it came from.
 *
 * This is an observation and not a record: it carries no identity, no dates and no count, and the
 * same place seen twice produces two equal observations. What is stored, deduplicated and expired is
 * a different model.
 *
 * [ip] is null where the deployment named a proxy whose header did not arrive. It deliberately does
 * not fall back to the address the socket connected from, which under a proxy is the proxy — one
 * missing `proxy_set_header` line would otherwise record the deployment's own edge as the address
 * every person signed in from, and read as a working feature.
 */
data class ObservedSecurityContext(
    val ip: String?,
    val userAgent: String?,
    val geo: SecurityContextGeo
)
